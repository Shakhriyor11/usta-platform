package uz.usta.platform.storage;

import java.util.UUID;
import java.util.function.Function;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3-mos obyekt saqlash. Bucket private; mijozlarga faqat qisqa muddatli presigned URL beriladi.
 */
public interface FileStorage {

    /**
     * Faylni tekshiradi (bo'sh emas, hajm, haqiqiy turi), yuklaydi va keyin {@code save} ni yangi qisqa DB
     * tranzaksiyasida bajaradi. Yuklash tranzaksiyadan tashqarida — chaqiruvchi ham tranzaksiyasiz chaqirishi
     * kerak, aks holda sekin yuklash DB ulanishi va qulflarni band qiladi. {@code save} yoki commit xato bersa,
     * fayl o'chiriladi (yetim fayl qolmaydi).
     *
     * @param ownerId fayl egasi — kalit {@code {folder}/{ownerId}/{uuid}.{ext}} ko'rinishida bo'ladi
     */
    <T> T storeAndSave(FilePurpose purpose, UUID ownerId, MultipartFile file, Function<StoredFile, T> save);

    /** Faylni o'qish uchun presigned GET URL ({@code app.storage.url-ttl} muddatga). */
    String url(String key);

    /**
     * Faylni o'chiradi: faol tranzaksiya bo'lsa — faqat muvaffaqiyatli commit'dan keyin, aks holda darhol.
     * Xatolar faqat logga yoziladi.
     */
    void deleteAfterCommit(String key);
}
