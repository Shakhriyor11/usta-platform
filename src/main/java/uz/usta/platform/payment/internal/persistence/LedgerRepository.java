package uz.usta.platform.payment.internal.persistence;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.payment.internal.domain.LedgerTransaction;

public interface LedgerRepository extends JpaRepository<LedgerTransaction, UUID> {

    @Query("select coalesce(sum(t.amount), 0) from LedgerTransaction t where t.masterId = :masterId")
    BigDecimal balance(@Param("masterId") UUID masterId);

    Page<LedgerTransaction> findByMasterId(UUID masterId, Pageable pageable);

    /** Buyurtmalar bo'yicha sof komissiya (charge manfiy + reversal musbat) — [orderId, sum]. */
    @Query("""
            select t.orderId, sum(t.amount) from LedgerTransaction t
            where t.orderId in :orderIds
              and t.type in (uz.usta.platform.payment.internal.domain.TransactionType.COMMISSION_CHARGE,
                             uz.usta.platform.payment.internal.domain.TransactionType.COMMISSION_REVERSAL)
            group by t.orderId""")
    List<Object[]> netByOrder(@Param("orderIds") Collection<UUID> orderIds);
}
