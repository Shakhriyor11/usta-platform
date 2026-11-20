package uz.usta.platform.complaint.internal.domain;

/** OPEN -> IN_REVIEW (admin oldi) -> RESOLVED | REJECTED. OPEN dan to'g'ridan-to'g'ri yopish ham mumkin. */
public enum ComplaintStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED;

    public boolean isOpen() {
        return this == OPEN || this == IN_REVIEW;
    }
}
