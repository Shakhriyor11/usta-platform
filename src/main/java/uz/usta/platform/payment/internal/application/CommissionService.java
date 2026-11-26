package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.payment.CommissionLedger;
import uz.usta.platform.payment.Commissions;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.LedgerTransaction;
import uz.usta.platform.payment.internal.domain.Payment;
import uz.usta.platform.payment.internal.persistence.CommissionReportRepository;
import uz.usta.platform.payment.internal.persistence.LedgerRepository;
import uz.usta.platform.payment.internal.persistence.PaymentRepository;

/**
 * Komissiyani yozish: {@code commission_reports} (davr bo'yicha), ledger (usta qarzi), {@code payments}
 * (mijoz to'lovi). Hammasi order'ning tranzaksiyasida — buyurtma COMMISSION bo'lsa, komissiya ham albatta bor.
 */
@Service
class CommissionService implements Commissions, CommissionLedger {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private final CommissionReportRepository reports;
    private final LedgerRepository ledger;
    private final PaymentRepository payments;
    private final CommissionCalculator calculator;
    private final BillingPeriods periods;
    private final PaymentGateway gateway;
    private final MasterAccount account;
    private final Clock clock;

    CommissionService(CommissionReportRepository reports, LedgerRepository ledger, PaymentRepository payments,
                      CommissionCalculator calculator, BillingPeriods periods, PaymentGateway gateway,
                      MasterAccount account, Clock clock) {
        this.reports = reports;
        this.ledger = ledger;
        this.payments = payments;
        this.calculator = calculator;
        this.periods = periods;
        this.gateway = gateway;
        this.account = account;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal charge(UUID orderId, UUID masterId, BigDecimal price) {
        Instant now = Instant.now(clock);
        account.lock(masterId);
        Optional<CommissionReport> previous = reports.findActiveByOrderId(orderId);
        if (previous.isPresent() && previous.get().getMasterId().equals(masterId)
                && previous.get().getBaseAmount().compareTo(price) == 0) {
            // nizo narx o'zgarmasdan hal qilindi: yozuvlar (jumladan to'langanlik holati) o'z joyida qoladi
            CommissionReport kept = previous.get();
            kept.release();
            reports.flush();
            account.applyCredit(masterId, now);
            return kept.getAmount();
        }
        // qayta hisoblashda dastlabki davr (va to'lov muddati) saqlanadi — nizo muddatni surmaydi
        LocalDate period = previous.map(CommissionReport::getPeriod).orElseGet(() -> periods.periodOf(now));
        previous.ifPresent(p -> cancel(p, null));
        BigDecimal amount = calculator.calculate(price);
        reports.saveAndFlush(new CommissionReport(masterId, orderId, price, calculator.percent(), amount, period));
        if (amount.signum() > 0) {
            ledger.save(LedgerTransaction.charge(masterId, orderId, amount));
        }
        Payment payment = payments.findByOrderId(orderId)
                .orElseGet(() -> new Payment(orderId, masterId, gateway.method(), gateway.provider()));
        PaymentGateway.Result r = gateway.capture(orderId, price);
        payment.record(price, r.status(), r.providerRef(), now);
        payments.save(payment);
        account.applyCredit(masterId, now);
        log.info("Commission for order {} (master {}): {} UZS ({}% of {})", orderId, masterId, amount,
                calculator.percent(), price);
        return amount;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(UUID orderId, UUID actorId) {
        lockedActive(orderId).ifPresent(r -> {
            cancel(r, actorId);
            payments.findByOrderId(orderId).ifPresent(p -> {
                p.refund();
                payments.save(p);
            });
            account.applyCredit(r.getMasterId(), Instant.now(clock));
            log.info("Commission for order {} reversed ({} UZS)", orderId, r.getAmount());
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void hold(UUID orderId) {
        lockedActive(orderId).ifPresent(r -> {
            r.hold();
            reports.flush();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> overdueDebtors() {
        return Set.copyOf(reports.findOverdueDebtors(periods.overdueBefore(Instant.now(clock))));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOverdueDebt(UUID masterId) {
        return reports.hasOverdueDebt(masterId, periods.overdueBefore(Instant.now(clock)));
    }

    /** Usta hisobini qulflab, buyurtmaning amaldagi komissiyasini o'qiydi (qulf — o'qishdan oldin). */
    private Optional<CommissionReport> lockedActive(UUID orderId) {
        Optional<UUID> masterId = reports.findActiveMasterId(orderId);
        masterId.ifPresent(account::lock);
        return masterId.flatMap(id -> reports.findActiveByOrderId(orderId));
    }

    /** Amaldagi komissiyani bekor qiladi va ledger'ga teskari yozuv qo'shadi (to'langan bo'lsa ham — ustaga kredit). */
    private void cancel(CommissionReport report, UUID actorId) {
        report.cancel();
        reports.saveAndFlush(report);
        if (report.getAmount().signum() > 0) {
            ledger.save(LedgerTransaction.reversal(report.getMasterId(), report.getOrderId(), report.getAmount(),
                    actorId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> activeCommissions(Collection<UUID> orderIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (!orderIds.isEmpty()) {
            reports.findActiveByOrderIds(orderIds).forEach(r -> result.put(r.getOrderId(), r.getAmount()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> ledgerNetCharges(Collection<UUID> orderIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Object[] row : ledger.netByOrder(orderIds)) {
                result.put((UUID) row[0], ((BigDecimal) row[1]).negate());
            }
        }
        return result;
    }
}
