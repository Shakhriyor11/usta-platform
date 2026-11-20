package uz.usta.platform.admin.internal.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.usta.platform.order.OrderApi;
import uz.usta.platform.order.OrderApi.CommissionedOrder;
import uz.usta.platform.payment.CommissionLedger;

/**
 * Hisob-kitobni solishtirish (reconciliation): yakunlangan har buyurtma uchun uch manba bir xil bo'lishi kerak —
 * buyurtmadagi komissiya ({@code orders.commission_amount}), amaldagi komissiya qatori
 * ({@code commission_reports}) va ledger'dagi sof qarz ({@code transactions}). Farqlar ro'yxat va
 * {@code finance.reconciliation.mismatches} metrikasi orqali ko'rinadi (Grafana alert uchun).
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final OrderApi orders;
    private final CommissionLedger ledger;
    private final Clock clock;
    private final ZoneId zone;
    private final AtomicInteger lastMismatches = new AtomicInteger();

    ReconciliationService(OrderApi orders, CommissionLedger ledger, Clock clock, MeterRegistry registry,
                          @Value("${app.payment.timezone:Asia/Tashkent}") ZoneId zone) {
        this.orders = orders;
        this.ledger = ledger;
        this.clock = clock;
        this.zone = zone;
        Gauge.builder("finance.reconciliation.mismatches", lastMismatches, AtomicInteger::get)
                .description("Oxirgi avtomatik solishtirishda topilgan nomuvofiqliklar").register(registry);
    }

    public record Mismatch(UUID orderId, UUID masterId, String orderStatus, BigDecimal orderCommission,
                           BigDecimal reportedCommission, BigDecimal ledgerNet) {
    }

    /** @param ok uchala manba barcha buyurtmalar bo'yicha mos */
    public record Report(LocalDate from, LocalDate to, boolean ok, int orders, BigDecimal totalOrderCommission,
                         BigDecimal totalReported, BigDecimal totalLedger, List<Mismatch> mismatches) {
    }

    /** @param from kiritiladi, {@code to} — kiritilmaydi (kunlar {@code app.payment.timezone} da) */
    public Report reconcile(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.atStartOfDay(zone).toInstant();
        List<CommissionedOrder> list = orders.completedBetween(start, end);
        List<UUID> ids = list.stream().map(CommissionedOrder::orderId).toList();
        Map<UUID, BigDecimal> reported = ledger.activeCommissions(ids);
        Map<UUID, BigDecimal> net = ledger.ledgerNetCharges(ids);

        BigDecimal totalOrder = BigDecimal.ZERO;
        BigDecimal totalReported = BigDecimal.ZERO;
        BigDecimal totalLedger = BigDecimal.ZERO;
        List<Mismatch> mismatches = new ArrayList<>();
        for (CommissionedOrder o : list) {
            BigDecimal expected = nz(o.commissionAmount());
            BigDecimal rep = nz(reported.get(o.orderId()));
            BigDecimal led = nz(net.get(o.orderId()));
            totalOrder = totalOrder.add(expected);
            totalReported = totalReported.add(rep);
            totalLedger = totalLedger.add(led);
            if (expected.compareTo(rep) != 0 || expected.compareTo(led) != 0) {
                mismatches.add(new Mismatch(o.orderId(), o.masterId(), o.status(), expected, rep, led));
            }
        }
        return new Report(from, to, mismatches.isEmpty(), list.size(), totalOrder, totalReported, totalLedger,
                mismatches);
    }

    /** Har kecha: oxirgi 35 kun (oylik davr + kechikkan nizolar). */
    @Scheduled(cron = "${app.payment.reconciliation-cron:0 30 2 * * *}", zone = "${app.payment.timezone:Asia/Tashkent}")
    @SchedulerLock(name = "finance-reconciliation", lockAtMostFor = "PT30M")
    public void nightly() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Report r = reconcile(today.minusDays(35), today.plusDays(1));
        lastMismatches.set(r.mismatches().size());
        if (r.ok()) {
            log.info("Finance reconciliation OK: {} orders, commission {} UZS", r.orders(), r.totalOrderCommission());
        } else {
            log.error("Finance reconciliation found {} mismatches: {}", r.mismatches().size(),
                    r.mismatches().stream().limit(20).toList());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
