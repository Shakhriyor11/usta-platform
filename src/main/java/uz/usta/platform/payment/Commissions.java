package uz.usta.platform.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Platforma komissiyasi (order moduli chaqiradi, uning tranzaksiyasida). Naqd to'lov modeli: mijoz ustaga
 * to'laydi, usta platformaga komissiya qarzdor bo'ladi — qarz ledger'da, haftalik hisobotlarda yig'iladi.
 */
public interface Commissions {

    /**
     * Yakunlangan buyurtma uchun komissiyani hisoblaydi va yozadi: komissiya qatori, ledger (qarz), to'lov yozuvi.
     * Qayta chaqirilsa (nizodan keyin narx o'zgardi) — oldingisi bekor qilinib, yangisi yoziladi.
     *
     * @param price yakuniy narx, UZS
     * @return komissiya summasi, UZS (2 xona)
     */
    BigDecimal charge(UUID orderId, UUID masterId, BigDecimal price);

    /** Komissiyani bekor qiladi (buyurtma nizodan keyin bekor qilindi). Komissiya bo'lmasa — hech narsa qilmaydi. */
    void reverse(UUID orderId, UUID actorId);
}
