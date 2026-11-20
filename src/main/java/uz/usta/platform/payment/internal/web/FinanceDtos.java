package uz.usta.platform.payment.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.domain.LedgerTransaction;
import uz.usta.platform.payment.internal.domain.TransactionType;

final class FinanceDtos {

    private FinanceDtos() {
    }

    @Schema(description = "balance < 0 — platformaga qarz (UZS). invoicedDue — yopilgan haftalar bo'yicha to'lanishi kerak, "
            + "accrued — joriy haftada yig'ilayotgan komissiya")
    record SummaryResponse(BigDecimal balance, BigDecimal invoicedDue, BigDecimal accrued, LocalDate currentPeriod,
                           String currency) {
    }

    record CommissionResponse(UUID id, UUID masterId, UUID orderId, BigDecimal baseAmount, BigDecimal ratePercent,
                              BigDecimal amount, LocalDate period, CommissionStatus status, Instant createdAt,
                              Instant invoicedAt, Instant paidAt) {
        static CommissionResponse of(CommissionReport c) {
            return new CommissionResponse(c.getId(), c.getMasterId(), c.getOrderId(), c.getBaseAmount(),
                    c.getRatePercent(), c.getAmount(), c.getPeriod(), c.getStatus(), c.getCreatedAt(),
                    c.getInvoicedAt(), c.getPaidAt());
        }
    }

    record TransactionResponse(UUID id, TransactionType type, BigDecimal amount, UUID orderId, String reference,
                               Instant createdAt) {
        static TransactionResponse of(LedgerTransaction t) {
            return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getOrderId(), t.getReference(),
                    t.getCreatedAt());
        }
    }

    record SettleRequest(
            @NotNull UUID masterId,
            @Schema(example = "2026-11-16", description = "Hafta boshi (dushanba)") @NotNull LocalDate period,
            @Schema(example = "38000") @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @Schema(example = "Kassa kvitansiyasi #1042") @Size(max = 128) String reference) {
    }

    record SettlementResponse(UUID masterId, LocalDate period, BigDecimal amount, int orders, BigDecimal balance) {
    }
}
