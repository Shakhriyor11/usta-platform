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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.payment.internal.domain.CommissionReport;
import uz.usta.platform.payment.internal.domain.CommissionStatus;

public interface CommissionReportRepository extends JpaRepository<CommissionReport, UUID>,
        JpaSpecificationExecutor<CommissionReport> {

    @Query("""
            select c from CommissionReport c
            where c.orderId = :orderId and c.status <> uz.usta.platform.payment.internal.domain.CommissionStatus.CANCELLED""")
    Optional<CommissionReport> findActiveByOrderId(@Param("orderId") UUID orderId);

    @Query("""
            select c from CommissionReport c
            where c.orderId in :orderIds and c.status <> uz.usta.platform.payment.internal.domain.CommissionStatus.CANCELLED""")
    List<CommissionReport> findActiveByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    Page<CommissionReport> findByMasterId(UUID masterId, Pageable pageable);

    @Query("""
            select coalesce(sum(c.amount), 0) from CommissionReport c
            where c.masterId = :masterId and c.status = :status""")
    BigDecimal sumByMasterAndStatus(@Param("masterId") UUID masterId, @Param("status") CommissionStatus status);

    /** Yopilishi kerak bo'lgan (davri tugagan) ACCRUED guruhlar: [masterId, period]. */
    @Query("""
            select distinct c.masterId, c.period from CommissionReport c
            where c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.ACCRUED and c.period < :current""")
    List<Object[]> findClosablePeriods(@Param("current") LocalDate currentPeriod);

    @Modifying(flushAutomatically = true)
    @Query("""
            update CommissionReport c set c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.INVOICED,
                c.invoicedAt = :now
            where c.masterId = :masterId and c.period = :period
              and c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.ACCRUED""")
    int invoice(@Param("masterId") UUID masterId, @Param("period") LocalDate period, @Param("now") Instant now);

    @Query("""
            select c from CommissionReport c
            where c.masterId = :masterId and c.period = :period and c.status = :status""")
    List<CommissionReport> findByMasterPeriodStatus(@Param("masterId") UUID masterId,
                                                    @Param("period") LocalDate period,
                                                    @Param("status") CommissionStatus status);

    @Modifying(flushAutomatically = true)
    @Query("""
            update CommissionReport c set c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.PAID,
                c.paidAt = :now
            where c.masterId = :masterId and c.period = :period
              and c.status = uz.usta.platform.payment.internal.domain.CommissionStatus.INVOICED""")
    int markPaid(@Param("masterId") UUID masterId, @Param("period") LocalDate period, @Param("now") Instant now);
}
