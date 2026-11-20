package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.util.UUID;
import uz.usta.platform.payment.internal.domain.PaymentMethod;
import uz.usta.platform.payment.internal.domain.PaymentStatus;

/**
 * To'lov provayderi adapteri. Hozir faqat {@link CashGateway} (naqd — mijoz ustaga to'laydi).
 * Click/Payme/Uzum qo'shilganda: yangi adapter (to'lov yaratish, callback/webhook imzo tekshiruvi, holat
 * so'rovi) — komissiya va ledger mantig'i o'zgarmaydi.
 */
public interface PaymentGateway {

    PaymentMethod method();

    String provider();

    /** Yakunlangan buyurtma uchun to'lovni qayd etish/undirish. */
    Result capture(UUID orderId, BigDecimal amount);

    record Result(PaymentStatus status, String providerRef) {
    }
}
