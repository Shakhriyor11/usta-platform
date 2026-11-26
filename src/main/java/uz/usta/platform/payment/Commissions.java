package uz.usta.platform.payment;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Platforma komissiyasi (order moduli chaqiradi, uning tranzaksiyasida). Naqd to'lov modeli: mijoz ustaga
 * to'laydi, usta platformaga komissiya qarzdor bo'ladi — qarz ledger'da, haftalik hisobotlarda yig'iladi.
 */
public interface Commissions {

    /**
     * Yakunlangan buyurtma uchun komissiyani hisoblaydi va yozadi: komissiya qatori, ledger (qarz), to'lov yozuvi.
     * Qayta chaqirilsa (nizodan keyin): narx o'zgarmagan bo'lsa — mavjud komissiya saqlanadi (to'langani qayta
     * talab qilinmaydi); o'zgargan bo'lsa — oldingisi bekor qilinib, yangisi o'sha hisob davriga yoziladi,
     * ortiqcha to'langan summa ustaning keyingi qarzidan chegiriladi.
     *
     * @param price yakuniy narx, UZS
     * @return komissiya summasi, UZS (2 xona)
     */
    BigDecimal charge(UUID orderId, UUID masterId, BigDecimal price);

    /** Komissiyani bekor qiladi (buyurtma nizodan keyin bekor qilindi). Komissiya bo'lmasa — hech narsa qilmaydi. */
    void reverse(UUID orderId, UUID actorId);

    /**
     * Nizo ochildi: komissiya hal bo'lguncha hisob-fakturaga va to'lanadigan summaga kirmaydi. Keyingi
     * {@link #charge} yoki {@link #reverse} uni yechadi. Komissiya hali yo'q bo'lsa — hech narsa qilmaydi.
     */
    void hold(UUID orderId);

    /** To'lov muddati o'tgan komissiya qarzi bor ustalar — ularga yangi taklif yuborilmaydi. */
    Set<UUID> overdueDebtors();

    /** Ustaning to'lov muddati o'tgan qarzi bormi — bo'lsa taklifni qabul qila olmaydi. */
    boolean hasOverdueDebt(UUID masterId);
}
