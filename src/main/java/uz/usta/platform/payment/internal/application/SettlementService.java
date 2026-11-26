package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.payment.PaymentEvents;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.domain.LedgerTransaction;
import uz.usta.platform.payment.internal.domain.PaymentErrorCode;
import uz.usta.platform.payment.internal.persistence.CommissionReportRepository;
import uz.usta.platform.payment.internal.persistence.LedgerRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;

/** Ustaning komissiya to'lovini qabul qilish (admin): davr bo'yicha to'liq summa. */
@Service
public class SettlementService {

    private final CommissionReportRepository reports;
    private final LedgerRepository ledger;
    private final BillingPeriods periods;
    private final MasterAccount account;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final Clock clock;

    SettlementService(CommissionReportRepository reports, LedgerRepository ledger, BillingPeriods periods,
                      MasterAccount account, OutboxPublisher outbox, AuditLog audit, Clock clock) {
        this.reports = reports;
        this.ledger = ledger;
        this.periods = periods;
        this.account = account;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    public record Settlement(UUID masterId, LocalDate period, BigDecimal amount, int orders, BigDecimal balance) {
    }

    /**
     * @param amount to'langan summa — davrning to'lanishi kerak summasiga (INVOICED, nizodagilarsiz, ortiqcha
     *               to'lov chegirilgan) teng bo'lishi shart: qisman to'lov qabul qilinmaydi, hisobot va ledger bir
     *               xil bo'lib qoladi
     */
    @Transactional
    public Settlement settle(UUID adminId, UUID masterId, LocalDate period, BigDecimal amount, String reference) {
        if (!periods.isPeriodStart(period)) {
            throw new BusinessException(PaymentErrorCode.INVALID_PERIOD, period);
        }
        account.lock(masterId); // parallel to'lov so'rovlari ketma-ket: ikkinchisi NOTHING_TO_SETTLE oladi
        List<CommissionReport> due = reports.findByMasterPeriodStatus(masterId, period, CommissionStatus.INVOICED);
        if (due.isEmpty()) {
            throw new BusinessException(PaymentErrorCode.NOTHING_TO_SETTLE, period);
        }
        BigDecimal invoiced = due.stream().map(CommissionReport::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = account.due(masterId, invoiced);
        if (outstanding.compareTo(amount) != 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH, Map.of("outstanding", outstanding),
                    outstanding.toPlainString());
        }
        Instant now = Instant.now(clock);
        if (outstanding.signum() > 0) {
            ledger.save(LedgerTransaction.payment(masterId, outstanding,
                    reference == null || reference.isBlank() ? "settlement:" + period : reference.strip(), adminId));
        }
        due.forEach(r -> r.markPaid(now));
        audit.record(adminId, "payment.commission-settled", "master", masterId,
                Map.of("period", period.toString(), "amount", outstanding.toPlainString()));
        outbox.publish(PaymentEvents.AGGREGATE, masterId, PaymentEvents.COMMISSION_SETTLED,
                new PaymentEvents.CommissionSettled(masterId, period, outstanding));
        reports.flush();
        ledger.flush();
        return new Settlement(masterId, period, outstanding, due.size(), ledger.balance(masterId));
    }
}
