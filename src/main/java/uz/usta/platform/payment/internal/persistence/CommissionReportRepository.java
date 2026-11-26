package uz.usta.platform.payment.internal.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;

public interface CommissionReportRepository extends JpaRepository<CommissionReport, UUID>,
        JpaSpecificationExecutor<CommissionReport> {

    /** Muddati o'tgan davrlarning to'lanmagan (nizoda bo'lmagan) komissiyalari. */
    String OVERDUE_ROWS = " from commission_reports r"
            + " where r.status = 'INVOICED' and not r.on_hold and r.period < :cutoff";

    /** Qarzdor — muddati o'tgan yig'indisi kreditdan katta bo'lgan usta. */
    String OVERDUE_EXCEEDS_CREDIT = " group by r.master_id having sum(r.amount) > greatest(0,"
            + " (select coalesce(sum(t.amount), 0) from transactions t where t.master_id = r.master_id)"
            + " + (select coalesce(sum(o.amount), 0) from commission_reports o"
            + "     where o.master_id = r.master_id and o.status in ('ACCRUED', 'INVOICED')))";

    @Query("""
            select c from CommissionReport c
            where c.orderId = :orderId and c.status <> uz.usta.platform.payment.internal.domain.CommissionStatus.CANCELLED""")
    Optional<CommissionReport> findActiveByOrderId(@Param("orderId") UUID orderId);

    /** Faqat usta ID si — qulf olinishidan oldin entity persistence context'ga tushib qolmasligi uchun. */
    @Query("""
            select c.masterId from CommissionReport c
            where c.orderId = :orderId and c.status <> uz.usta.platform.payment.internal.domain.CommissionStatus.CANCELLED""")
    Optional<UUID> findActiveMasterId(@Param("orderId") UUID orderId);

    @Query("""
            select c from CommissionReport c
            where c.orderId in :orderIds and c.status <> uz.usta.platform.payment.internal.domain.CommissionStatus.CANCELLED""")
    List<CommissionReport> findActiveByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    Page<CommissionReport> findByMasterId(UUID masterId, Pageable pageable);

    /** Nizoda bo'lmagan qatorlar yig'indisi. */
    @Query("""
            select coalesce(sum(c.amount), 0) from CommissionReport c
            where c.masterId = :masterId and c.status = :status and c.onHold = false""")
    BigDecimal sumByMasterAndStatus(@Param("masterId") UUID masterId, @Param("status") CommissionStatus status);

    /** Barcha ochiq (ACCRUED va INVOICED, nizodagilar ham) komissiyalar — kredit hisobi uchun. */
    @Query("""
            select coalesce(sum(c.amount), 0) from CommissionReport c
            where c.masterId = :masterId
              and c.status in (uz.usta.platform.payment.internal.domain.CommissionStatus.ACCRUED,
                               uz.usta.platform.payment.internal.domain.CommissionStatus.INVOICED)""")
    BigDecimal sumOpen(@Param("masterId") UUID masterId);

    @Query("""
            select c from CommissionReport c
            where c.masterId = :masterId and c.status = :status
            order by c.period, c.createdAt, c.id""")
    List<CommissionReport> findByMasterStatus(@Param("masterId") UUID masterId,
                                              @Param("status") CommissionStatus status);

    /** Yopilishi kerak bo'lgan (davri tugagan) ACCRUED guruhlar: [masterId, period]. */
    @Query("""
            select distinct c.masterId, c.period from CommissionReport c
            where c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.ACCRUED and c.period < :current
              and c.onHold = false""")
    List<Object[]> findClosablePeriods(@Param("current") LocalDate currentPeriod);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CommissionReport c set c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.INVOICED,
                c.invoicedAt = :now, c.version = c.version + 1
            where c.masterId = :masterId and c.period = :period and c.onHold = false
              and c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.ACCRUED""")
    int invoice(@Param("masterId") UUID masterId, @Param("period") LocalDate period, @Param("now") Instant now);

    /** Nizoda bo'lmagan qatorlar. */
    @Query("""
            select c from CommissionReport c
            where c.masterId = :masterId and c.period = :period and c.status = :status and c.onHold = false""")
    List<CommissionReport> findByMasterPeriodStatus(@Param("masterId") UUID masterId,
                                                    @Param("period") LocalDate period,
                                                    @Param("status") CommissionStatus status);

    @NativeQuery("select r.master_id" + OVERDUE_ROWS + OVERDUE_EXCEEDS_CREDIT)
    List<UUID> findOverdueDebtors(@Param("cutoff") LocalDate cutoff);

    @NativeQuery("select exists (select 1" + OVERDUE_ROWS + " and r.master_id = :masterId" + OVERDUE_EXCEEDS_CREDIT + ")")
    boolean hasOverdueDebt(@Param("masterId") UUID masterId, @Param("cutoff") LocalDate cutoff);
}
