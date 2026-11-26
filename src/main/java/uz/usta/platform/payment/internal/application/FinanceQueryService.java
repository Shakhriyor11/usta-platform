package uz.usta.platform.payment.internal.application;

import jakarta.persistence.criteria.Predicate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.domain.LedgerTransaction;
import uz.usta.platform.payment.internal.persistence.CommissionReportRepository;
import uz.usta.platform.payment.internal.persistence.LedgerRepository;

@Service
public class FinanceQueryService {

    private final CommissionReportRepository reports;
    private final LedgerRepository ledger;
    private final BillingPeriods periods;
    private final MasterAccount account;
    private final JdbcClient jdbc;

    FinanceQueryService(CommissionReportRepository reports, LedgerRepository ledger, BillingPeriods periods,
                        MasterAccount account, JdbcClient jdbc) {
        this.reports = reports;
        this.ledger = ledger;
        this.periods = periods;
        this.account = account;
        this.jdbc = jdbc;
    }

    /**
     * @param balance     ledger yig'indisi: manfiy — usta platformaga qarzdor
     * @param invoicedDue yopilgan davrlar bo'yicha to'lanishi kerak (nizodagilarsiz, ortiqcha to'lov chegirilgan)
     * @param accrued     joriy (hali yopilmagan) davrda yig'ilgan komissiya
     * @param overdue     to'lov muddati o'tgani uchun yangi buyurtmalar bloklanganmi
     */
    public record Summary(BigDecimal balance, BigDecimal invoicedDue, BigDecimal accrued, LocalDate currentPeriod,
                          boolean overdue) {
    }

    /**
     * Admin uchun: usta × davr bo'yicha yig'ma (hisob-faktura).
     *
     * @param invoiced yopilgan, to'lanmagan komissiya (nizodagilarsiz)
     * @param onHold   nizo hal bo'lguncha kutayotgan komissiya
     * @param due      to'lov qabul qilishda kiritiladigan summa ({@code invoiced} dan ortiqcha to'lov chegirilgan)
     */
    public record Invoice(
            UUID masterId, LocalDate period, int orders, BigDecimal accrued,
            @Schema(description = "Yopilgan, to'lanmagan komissiya (nizodagilarsiz)") BigDecimal invoiced,
            BigDecimal paid,
            @Schema(description = "Nizo hal bo'lguncha kutayotgan komissiya") BigDecimal onHold,
            @Schema(description = "To'lov qabul qilishda kiritiladigan summa (ortiqcha to'lov chegirilgan)")
            BigDecimal due,
            LocalDate dueDate) {
    }

    public record Filter(UUID masterId, LocalDate period, CommissionStatus status) {
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID masterId, Instant now) {
        BigDecimal invoiced = reports.sumByMasterAndStatus(masterId, CommissionStatus.INVOICED);
        return new Summary(ledger.balance(masterId), account.due(masterId, invoiced),
                reports.sumByMasterAndStatus(masterId, CommissionStatus.ACCRUED), periods.periodOf(now),
                reports.hasOverdueDebt(masterId, periods.overdueBefore(now)));
    }

    @Transactional(readOnly = true)
    public Page<CommissionReport> commissions(Filter f, Pageable pageable) {
        Specification<CommissionReport> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.masterId() != null) {
                p.add(cb.equal(root.get("masterId"), f.masterId()));
            }
            if (f.period() != null) {
                p.add(cb.equal(root.get("period"), f.period()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return reports.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LedgerTransaction> transactions(UUID masterId, Pageable pageable) {
        return ledger.findByMasterId(masterId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoices(LocalDate period, UUID masterId) {
        Map<UUID, BigDecimal> credits = new HashMap<>();
        return jdbc.sql("""
                        select master_id, period, count(*) as orders,
                               coalesce(sum(amount) filter (where status = 'ACCRUED' and not on_hold), 0)  as accrued,
                               coalesce(sum(amount) filter (where status = 'INVOICED' and not on_hold), 0) as invoiced,
                               coalesce(sum(amount) filter (where status = 'PAID'), 0)                     as paid,
                               coalesce(sum(amount) filter (where status <> 'PAID' and on_hold), 0)        as held
                        from commission_reports
                        where status <> 'CANCELLED' and (cast(:period as date) is null or period = :period)
                          and (cast(:masterId as uuid) is null or master_id = :masterId)
                        group by master_id, period
                        order by period desc, invoiced desc""")
                .param("period", period).param("masterId", masterId)
                .query((rs, i) -> new Row(rs.getObject("master_id", UUID.class),
                        rs.getObject("period", LocalDate.class), rs.getInt("orders"), rs.getBigDecimal("accrued"),
                        rs.getBigDecimal("invoiced"), rs.getBigDecimal("paid"), rs.getBigDecimal("held")))
                .list().stream()
                .map(r -> new Invoice(r.masterId(), r.period(), r.orders(), r.accrued(), r.invoiced(), r.paid(),
                        r.held(), r.invoiced()
                        .subtract(credits.computeIfAbsent(r.masterId(), account::credit)).max(BigDecimal.ZERO),
                        periods.dueDate(r.period())))
                .toList();
    }

    private record Row(UUID masterId, LocalDate period, int orders, BigDecimal accrued, BigDecimal invoiced,
                       BigDecimal paid, BigDecimal held) {
    }
}
