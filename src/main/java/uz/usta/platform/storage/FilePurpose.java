package uz.usta.platform.storage;

import java.util.Set;

/**
 * Fayl maqsadi — ruxsat etilgan turlar va maksimal hajmni belgilaydi. Tur mijoz yuborgan
 * {@code Content-Type} bo'yicha emas, faylning birinchi baytlari (magic bytes) bo'yicha aniqlanadi.
 */
public enum FilePurpose {
    AVATAR("avatars", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP), 5L * 1024 * 1024),
    VERIFICATION_DOCUMENT("verification", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP, MediaTypes.PDF),
            10L * 1024 * 1024),
    PORTFOLIO("portfolio", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP), 10L * 1024 * 1024),
    ORDER_ATTACHMENT("orders", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP, MediaTypes.MP4),
            50L * 1024 * 1024);

    private final String folder;
    private final Set<String> allowedTypes;
    private final long maxSizeBytes;

    FilePurpose(String folder, Set<String> allowedTypes, long maxSizeBytes) {
        this.folder = folder;
        this.allowedTypes = allowedTypes;
        this.maxSizeBytes = maxSizeBytes;
    }

    public String folder() {
        return folder;
    }

    public Set<String> allowedTypes() {
        return allowedTypes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    /** Qo'llab-quvvatlanadigan media turlari. */
    public static final class MediaTypes {
        public static final String JPEG = "image/jpeg";
        public static final String PNG = "image/png";
        public static final String WEBP = "image/webp";
        public static final String PDF = "application/pdf";
        public static final String MP4 = "video/mp4";

        private MediaTypes() {
        }
    }
}
