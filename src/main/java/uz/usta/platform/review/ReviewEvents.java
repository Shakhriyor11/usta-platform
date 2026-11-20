package uz.usta.platform.review;

import java.util.UUID;

/** Review modulining outbox eventlari. */
public final class ReviewEvents {

    public static final String AGGREGATE = "review";
    public static final String REVIEW_CREATED = "review.created";

    private ReviewEvents() {
    }

    public record ReviewCreated(UUID reviewId, UUID orderId, UUID customerId, UUID masterId, int rating) {
    }
}
