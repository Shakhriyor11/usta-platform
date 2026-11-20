package uz.usta.platform.storage;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3-mos obyekt saqlash. Bucket private; mijozlarga faqat qisqa muddatli presigned URL beriladi.
 */
public interface FileStorage {

    /**
     * Faylni tekshiradi (bo'sh emas, hajm, haqiqiy turi) va saqlaydi. Faol DB tranzaksiyasi ichida
     * chaqirilsa va tranzaksiya rollback bo'lsa — fayl avtomatik o'chiriladi (yetim fayl qolmaydi).
     *
     * @param ownerId fayl egasi — kalit {@code {folder}/{ownerId}/{uuid}.{ext}} ko'rinishida bo'ladi
     */
    StoredFile store(FilePurpose purpose, UUID ownerId, MultipartFile file);

    /** Faylni o'qish uchun presigned GET URL ({@code app.storage.url-ttl} muddatga). */
    String url(String key);

    /**
     * Faylni o'chiradi: faol tranzaksiya bo'lsa — faqat muvaffaqiyatli commit'dan keyin, aks holda darhol.
     * Xatolar faqat logga yoziladi.
     */
    void deleteAfterCommit(String key);
}
