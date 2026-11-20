package uz.usta.platform.review.internal.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.order.OrderApi;
import uz.usta.platform.profile.MasterProfiles;
import uz.usta.platform.review.ReviewEvents;
import uz.usta.platform.review.internal.domain.Review;
import uz.usta.platform.review.internal.domain.ReviewErrorCode;
import uz.usta.platform.review.internal.persistence.ReviewRepository;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;

@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final OrderApi orders;
    private final MasterProfiles masters;
    private final OutboxPublisher outbox;

    ReviewService(ReviewRepository reviews, OrderApi orders, MasterProfiles masters, OutboxPublisher outbox) {
        this.reviews = reviews;
        this.orders = orders;
        this.masters = masters;
        this.outbox = outbox;
    }

    /**
     * Bitta tranzaksiyada: buyurtma qulflanadi va tekshiriladi (egasi, COMPLETED/COMMISSION) -> baho saqlanadi
     * -> ustaning rating_avg/rating_count yangilanadi -> buyurtma REVIEWED -> outbox event.
     * Parallel ikki so'rovdan ikkinchisi qulfda kutadi va ALREADY_REVIEWED oladi (+ UNIQUE(order_id) himoyasi).
     */
    @Transactional
    public Review create(UUID customerId, UUID orderId, int rating, String comment) {
        OrderApi.ReviewTarget target = orders.lockForReview(orderId, customerId);
        if (reviews.existsByOrderId(orderId)) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }
        Review review = reviews.saveAndFlush(new Review(orderId, customerId, target.masterId(), rating,
                comment == null || comment.isBlank() ? null : comment.strip()));
        masters.applyRating(target.masterId(), rating);
        orders.markReviewed(orderId, customerId);
        outbox.publish(ReviewEvents.AGGREGATE, review.getId(), ReviewEvents.REVIEW_CREATED,
                new ReviewEvents.ReviewCreated(review.getId(), orderId, customerId, target.masterId(), rating));
        return review;
    }

    @Transactional(readOnly = true)
    public Review forOrder(UUID customerId, UUID orderId) {
        return reviews.findByOrderId(orderId).filter(r -> r.getCustomerId().equals(customerId))
                .orElseThrow(() -> BusinessException.notFound("Review", orderId));
    }

    /** Ustaning sharhlari (mijozlarga ko'rinadigan usta uchun). */
    @Transactional(readOnly = true)
    public Page<Review> forMaster(UUID masterId, Pageable pageable) {
        if (!masters.isPublic(masterId)) {
            throw BusinessException.notFound("Master", masterId);
        }
        return reviews.findByMasterId(masterId, pageable);
    }
}
