package uz.usta.platform.complaint;

import java.util.UUID;

/** Complaint modulining outbox eventlari. */
public final class ComplaintEvents {

    public static final String AGGREGATE = "complaint";
    public static final String COMPLAINT_FILED = "complaint.filed";
    public static final String COMPLAINT_CLOSED = "complaint.closed";

    private ComplaintEvents() {
    }

    public record ComplaintFiled(UUID complaintId, UUID orderId, UUID authorId, UUID againstUserId, String reason,
                                 boolean dispute) {
    }

    /** @param status RESOLVED yoki REJECTED */
    public record ComplaintClosed(UUID complaintId, UUID orderId, UUID authorId, UUID againstUserId, String status) {
    }
}
