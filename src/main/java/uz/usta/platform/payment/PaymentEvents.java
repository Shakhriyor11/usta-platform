package uz.usta.platform.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Payment modulining outbox eventlari. */
public final class PaymentEvents {

    public static final String AGGREGATE = "commission";
    /** Haftalik davr yopildi — ustaga to'lanadigan komissiya hisobi. */
    public static final String COMMISSION_INVOICED = "payment.commission-invoiced";
    /** Admin ustaning to'lovini qabul qildi. */
    public static final String COMMISSION_SETTLED = "payment.commission-settled";

    private PaymentEvents() {
    }

    public record CommissionInvoiced(UUID masterId, LocalDate period, BigDecimal amount, int orders,
                                     LocalDate dueDate) {
    }

    public record CommissionSettled(UUID masterId, LocalDate period, BigDecimal amount) {
    }
}
