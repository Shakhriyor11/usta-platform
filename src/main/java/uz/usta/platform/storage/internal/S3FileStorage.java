package uz.usta.platform.storage.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.util.Uuids;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;
import uz.usta.platform.storage.StorageErrorCode;
import uz.usta.platform.storage.StoredFile;

@Component
class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);
    /** Har qanday maqsaddagi rasm uchun yuqori chegara (video — maqsad limiti bo'yicha, oqim bilan yuklanadi). */
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    private final TransactionTemplate tx;
    /** Parallel katta rasmlar xotirani tugatib qo'ymasligi uchun (8 × 20 MB). */
    private final Semaphore imageSlots = new Semaphore(8);

    S3FileStorage(S3Client s3, S3Presigner presigner, StorageProperties props, TransactionTemplate tx) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
        this.tx = tx;
    }

    @Override
    public <T> T storeAndSave(FilePurpose purpose, UUID ownerId, MultipartFile file, Function<StoredFile, T> save) {
        StoredFile stored = upload(purpose, ownerId, file);
        try {
            return tx.execute(s -> save.apply(stored));
        } catch (RuntimeException e) {
            deleteNow(stored.key());
            throw e;
        }
    }

    private StoredFile upload(FilePurpose purpose, UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(StorageErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > purpose.maxSizeBytes()) {
            throw new BusinessException(StorageErrorCode.FILE_TOO_LARGE, humanSize(purpose.maxSizeBytes()));
        }
        String type;
        try (InputStream in = file.getInputStream()) {
            type = ContentSniffer.detect(in.readNBytes(ContentSniffer.HEADER_SIZE))
                    .filter(purpose.allowedTypes()::contains)
                    .orElseThrow(() -> new BusinessException(StorageErrorCode.FILE_TYPE_NOT_ALLOWED,
                            String.join(", ", purpose.allowedTypes())));
        } catch (IOException e) {
            throw new BusinessException(StorageErrorCode.FILE_EMPTY);
        }
        String key = "%s/%s/%s.%s".formatted(purpose.folder(), ownerId, Uuids.v7(), ContentSniffer.extension(type));
        if (type.startsWith("image/")) {
            return uploadImage(purpose, type, key, file);
        }
        // SDK qayta urinishda (retry) oqimni boshidan o'qiy olishi uchun har safar yangi InputStream ochiladi.
        // fromInputStream(...) mark/reset qo'llamaydigan oqimda retry paytida IllegalStateException beradi.
        ContentStreamProvider body = () -> {
            try {
                return file.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return send(key, type, body, file.getSize());
    }

    /** Rasm tozalash uchun xotiraga o'qiladi — hajmi va bir vaqtdagi soni cheklangan. */
    private StoredFile uploadImage(FilePurpose purpose, String type, String key, MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(StorageErrorCode.FILE_TOO_LARGE, humanSize(MAX_IMAGE_BYTES));
        }
        imageSlots.acquireUninterruptibly();
        try {
            byte[] clean = sanitizedImage(purpose, type, file);
            return send(key, type, ContentStreamProvider.fromByteArray(clean), clean.length);
        } finally {
            imageSlots.release();
        }
    }

    private StoredFile send(String key, String type, ContentStreamProvider body, long size) {
        try {
            put(key, type, body, size);
        } catch (NoSuchBucketException e) {
            // bucket hali yaratilmagan (masalan MinIO ilovadan keyin ko'tarilgan) — yaratib, bir marta qayta urinamiz
            if (!props.createBucket()) {
                throw unavailable(e);
            }
            try {
                ensureBucket();
                put(key, type, body, size);
            } catch (SdkException retryError) {
                throw unavailable(retryError);
            }
        } catch (SdkException e) {
            throw unavailable(e);
        }
        return new StoredFile(key, type, size);
    }

    /** Rasm tuzilishi va o'lchami tekshiriladi, metama'lumotlari olib tashlanadi ({@link ImageSanitizer}). */
    private static byte[] sanitizedImage(FilePurpose purpose, String type, MultipartFile file) {
        byte[] raw;
        try {
            raw = file.getBytes(); // hajm yuqorida cheklangan
        } catch (IOException e) {
            throw new BusinessException(StorageErrorCode.FILE_EMPTY);
        }
        ImageSanitizer.Sanitized image = ImageSanitizer.sanitize(type, raw)
                .orElseThrow(() -> new BusinessException(StorageErrorCode.IMAGE_INVALID));
        FilePurpose.ImageRules rules = purpose.imageRules();
        if (Math.min(image.width(), image.height()) < rules.minSide()
                || Math.max(image.width(), image.height()) > rules.maxSide()) {
            throw new BusinessException(StorageErrorCode.IMAGE_DIMENSIONS, Integer.toString(rules.minSide()),
                    Integer.toString(rules.maxSide()));
        }
        if (rules.square() && image.width() != image.height()) {
            throw new BusinessException(StorageErrorCode.IMAGE_NOT_SQUARE);
        }
        return image.data();
    }

    private static String humanSize(long bytes) {
        return bytes >= 1024 * 1024 ? bytes / (1024 * 1024) + " MB" : bytes / 1024 + " KB";
    }

    private void put(String key, String type, ContentStreamProvider body, long size) {
        PutObjectRequest req = PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(type).build();
        try {
            s3.putObject(req, RequestBody.fromContentProvider(body, size, type));
        } catch (UncheckedIOException e) {
            throw new BusinessException(StorageErrorCode.FILE_EMPTY);
        }
    }

    @Override
    public String url(String key) {
        if (key == null) {
            return null;
        }
        return presigner.presignGetObject(b -> b.signatureDuration(props.urlTtl())
                        .getObjectRequest(g -> g.bucket(props.bucket()).key(key)))
                .url().toString();
    }

    @Override
    public void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteNow(key);
                }
            });
        } else {
            deleteNow(key);
        }
    }

    private void deleteNow(String key) {
        try {
            s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
        } catch (SdkException e) {
            log.warn("Failed to delete object {}: {}", key, e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void initBucket() {
        if (props.createBucket()) {
            try {
                ensureBucket();
            } catch (SdkException e) {
                log.warn("Object storage not reachable on startup ({}); bucket will be created on first upload",
                        e.getMessage());
            }
        }
    }

    private void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(props.bucket()));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(props.bucket()));
            log.info("Created bucket {}", props.bucket());
        }
    }

    private static BusinessException unavailable(SdkException e) {
        log.error("Object storage error: {}", e.getMessage());
        return new BusinessException(StorageErrorCode.STORAGE_UNAVAILABLE);
    }
}
