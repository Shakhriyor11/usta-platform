package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import uz.usta.platform.payment.PaymentEvents;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.persistence.CommissionReportRepository;
import uz.usta.platform.shared.outbox.OutboxPublisher;

/**
 * Haftalik davrlarni yopish: tugagan haftaning ACCRUED komissiyalari ustalar bo'yicha INVOICED qilinadi va
 * ustaga to'lov summasi va muddati yuboriladi. Idempotent — har soat ishlaydi, yopilmaganlarini yopadi
 * (server o'chib qolgan bo'lsa ham keyingi ishga tushishda yetib oladi).
 */
@Service
public class InvoicingService {

    private static final Logger log = LoggerFactory.getLogger(InvoicingService.class);

    private final CommissionReportRepository reports;
    private final BillingPeriods periods;
    private final OutboxPublisher outbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    InvoicingService(CommissionReportRepository reports, BillingPeriods periods, OutboxPublisher outbox,
                     TransactionTemplate tx, Clock clock) {
        this.reports = reports;
        this.periods = periods;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.payment.invoicing-cron:0 5 * * * *}", zone = "${app.payment.timezone:Asia/Tashkent}")
    @SchedulerLock(name = "commission-invoicing", lockAtMostFor = "PT10M")
    public void scheduled() {
        int n = closeEndedPeriods();
        if (n > 0) {
            log.info("Commission invoicing: {} master-periods invoiced", n);
        }
    }

    /** @return yopilgan (usta, davr) juftliklari soni */
    public int closeEndedPeriods() {
        Instant now = Instant.now(clock);
        LocalDate current = periods.periodOf(now);
        int closed = 0;
        for (Object[] row : reports.findClosablePeriods(current)) {
            UUID masterId = (UUID) row[0];
            LocalDate period = (LocalDate) row[1];
            Integer n = tx.execute(s -> invoice(masterId, period, now));
            closed += n == null ? 0 : n;
        }
        return closed;
    }

    private int invoice(UUID masterId, LocalDate period, Instant now) {
        if (reports.invoice(masterId, period, now) == 0) {
            return 0; // parallel instansiya allaqachon yopgan
        }
        List<CommissionReport> rows = reports.findByMasterPeriodStatus(masterId, period, CommissionStatus.INVOICED);
        BigDecimal total = rows.stream().map(CommissionReport::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        outbox.publish(PaymentEvents.AGGREGATE, masterId, PaymentEvents.COMMISSION_INVOICED,
                new PaymentEvents.CommissionInvoiced(masterId, period, total, rows.size(), periods.dueDate(period)));
        return 1;
    }
}
