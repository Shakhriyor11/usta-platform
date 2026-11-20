package uz.usta.platform.complaint.internal.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.complaint.internal.domain.Complaint;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {

    @Query("""
            select count(c) > 0 from Complaint c where c.orderId = :orderId and c.authorId = :authorId
              and c.status in (uz.usta.platform.complaint.internal.domain.ComplaintStatus.OPEN,
                               uz.usta.platform.complaint.internal.domain.ComplaintStatus.IN_REVIEW)""")
    boolean hasOpen(@Param("orderId") UUID orderId, @Param("authorId") UUID authorId);

    Page<Complaint> findByAuthorId(UUID authorId, Pageable pageable);

    long countByAgainstUserId(UUID againstUserId);
}
