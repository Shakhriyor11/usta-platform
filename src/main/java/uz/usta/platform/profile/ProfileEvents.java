package uz.usta.platform.profile;

import java.util.UUID;

/** Profile modulining outbox eventlari. */
public final class ProfileEvents {

    public static final String AGGREGATE = "master_profile";
    public static final String VERIFICATION_SUBMITTED = "profile.master-verification-submitted";
    public static final String MASTER_APPROVED = "profile.master-approved";
    public static final String MASTER_REJECTED = "profile.master-rejected";
    /** Qidiruv keshini invalidatsiya qilish uchun (profil, faollik, ish hududi o'zgardi). */
    public static final String MASTER_PROFILE_CHANGED = "profile.master-changed";

    private ProfileEvents() {
    }

    public record VerificationSubmitted(UUID masterId) {
    }

    public record MasterApproved(UUID masterId, UUID approvedBy) {
    }

    public record MasterRejected(UUID masterId, UUID rejectedBy, String reason) {
    }

    public record MasterProfileChanged(UUID masterId) {
    }
}
