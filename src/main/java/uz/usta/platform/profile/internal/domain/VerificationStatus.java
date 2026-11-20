package uz.usta.platform.profile.internal.domain;

/**
 * Usta verifikatsiyasi: NOT_SUBMITTED -> PENDING -> APPROVED | REJECTED; REJECTED -> PENDING (qayta yuborish);
 * APPROVED -> REJECTED (admin tasdiqni bekor qilishi mumkin).
 */
public enum VerificationStatus {
    NOT_SUBMITTED,
    PENDING,
    APPROVED,
    REJECTED
}
