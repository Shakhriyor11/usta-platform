package uz.usta.platform.profile.internal.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.persistence.AuditableEntity;

/**
 * Usta profili. {@code work_location} (geography) va {@code is_verified} DB tomonidan hisoblanadi
 * (GENERATED ustunlar), shuning uchun bu yerda map qilinmagan. Reyting maydonlarini faqat review moduli
 * yangilaydi (P3).
 */
@Entity
@Table(name = "master_profiles")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class MasterProfile extends AuditableEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "experience_years", nullable = false)
    private short experienceYears;

    @Column(name = "avatar_key", length = 512)
    private String avatarKey;

    @Column(name = "work_latitude")
    private Double workLatitude;

    @Column(name = "work_longitude")
    private Double workLongitude;

    @Column(name = "work_radius_km", nullable = false)
    private short workRadiusKm = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    private VerificationStatus verificationStatus = VerificationStatus.NOT_SUBMITTED;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2, insertable = false, updatable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private int ratingCount;

    protected MasterProfile() {
    }

    public MasterProfile(UUID userId, String fullName) {
        super(userId);
        this.fullName = fullName;
    }

    public void updateDetails(String fullName, String bio, int experienceYears, Double latitude, Double longitude,
                              int workRadiusKm) {
        this.fullName = fullName;
        this.bio = bio;
        this.experienceYears = (short) experienceYears;
        this.workLatitude = latitude;
        this.workLongitude = longitude;
        this.workRadiusKm = (short) workRadiusKm;
    }

    public String replaceAvatar(String key) {
        String old = avatarKey;
        avatarKey = key;
        return old;
    }

    /** Hujjatlarni faqat ko'rib chiqilmayotgan va tasdiqlanmagan holatda o'zgartirish mumkin. */
    public boolean documentsEditable() {
        return verificationStatus == VerificationStatus.NOT_SUBMITTED
                || verificationStatus == VerificationStatus.REJECTED;
    }

    public boolean hasWorkArea() {
        return workLatitude != null && workLongitude != null;
    }

    public void submitForVerification(Instant now) {
        if (!documentsEditable()) {
            throw invalidState();
        }
        if (!hasWorkArea()) {
            throw new BusinessException(ProfileErrorCode.VERIFICATION_WORK_AREA_REQUIRED);
        }
        verificationStatus = VerificationStatus.PENDING;
        submittedAt = now;
        rejectionReason = null;
    }

    public void approve(UUID adminId, Instant now) {
        if (verificationStatus != VerificationStatus.PENDING) {
            throw invalidState();
        }
        verificationStatus = VerificationStatus.APPROVED;
        verifiedAt = now;
        verifiedBy = adminId;
        rejectionReason = null;
    }

    public void reject(UUID adminId, String reason, Instant now) {
        if (verificationStatus != VerificationStatus.PENDING && verificationStatus != VerificationStatus.APPROVED) {
            throw invalidState();
        }
        verificationStatus = VerificationStatus.REJECTED;
        verifiedAt = now;
        verifiedBy = adminId;
        rejectionReason = reason;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPublic() {
        return verificationStatus == VerificationStatus.APPROVED && active;
    }

    private BusinessException invalidState() {
        return new BusinessException(ProfileErrorCode.VERIFICATION_INVALID_STATE, verificationStatus.name());
    }

    public String getFullName() {
        return fullName;
    }

    public String getBio() {
        return bio;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public Double getWorkLatitude() {
        return workLatitude;
    }

    public Double getWorkLongitude() {
        return workLongitude;
    }

    public int getWorkRadiusKm() {
        return workRadiusKm;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public boolean isActive() {
        return active;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }
}
