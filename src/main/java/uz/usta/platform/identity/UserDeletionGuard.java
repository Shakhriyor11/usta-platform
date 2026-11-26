package uz.usta.platform.identity;

import java.util.UUID;

/**
 * Hisobni o'chirishga to'siq bo'ladigan holatlar (tugallanmagan ish, qarz...). Har modul o'z tekshiruvini
 * amalga oshiradi; o'chirish tranzaksiyasida chaqiriladi.
 */
public interface UserDeletionGuard {

    /** @throws uz.usta.platform.shared.error.BusinessException hozircha o'chirib bo'lmasa */
    void requireDeletable(UUID userId);
}
