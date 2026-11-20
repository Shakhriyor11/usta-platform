package uz.usta.platform.review.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.review.internal.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<Review> findByOrderId(UUID orderId);

    Page<Review> findByMasterId(UUID masterId, Pageable pageable);
}
