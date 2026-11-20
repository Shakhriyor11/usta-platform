package uz.usta.platform.payment;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Solishtirish (reconciliation) uchun: buyurtmalar bo'yicha amaldagi komissiyalar. */
public interface CommissionLedger {

    /** Amaldagi (bekor qilinmagan) komissiya summalari; komissiyasi yo'q buyurtmalar natijada bo'lmaydi. */
    Map<UUID, BigDecimal> activeCommissions(Collection<UUID> orderIds);

    /** Ledger'dagi buyurtma bo'yicha sof komissiya (charge + reversal, musbat son). */
    Map<UUID, BigDecimal> ledgerNetCharges(Collection<UUID> orderIds);
}
