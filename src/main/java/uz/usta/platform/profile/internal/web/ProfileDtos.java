package uz.usta.platform.profile.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.usta.platform.profile.internal.domain.DocumentType;
import uz.usta.platform.profile.internal.domain.VerificationStatus;
import uz.usta.platform.shared.validation.PlainText;
import uz.usta.platform.shared.web.GeoPoint;

final class ProfileDtos {

    private ProfileDtos() {
    }

    // ---------------------------------------------------------------- mijoz

    record CustomerProfileRequest(
            @Schema(example = "Aziz Karimov") @NotBlank @Size(min = 2, max = 120) @PlainText String fullName) {
    }

    record CustomerProfileResponse(UUID userId, String fullName, String avatarUrl, Instant createdAt,
                                   Instant updatedAt) {
    }

    record AddressRequest(
            @Schema(example = "Uy") @NotBlank @Size(max = 60) @PlainText String title,
            @Schema(example = "Toshkent, Chilonzor tumani, 9-kvartal, 12-uy") @NotBlank @Size(max = 500) @PlainText String address,
            @Schema(example = "3-podyezd, 4-qavat, 45-xonadon") @Size(max = 255) @PlainText String details,
            @NotNull @Valid GeoPoint location,
            @Schema(description = "true — asosiy manzil qilib belgilash") Boolean isDefault) {
    }

    record AddressResponse(UUID id, String title, String address, String details, GeoPoint location,
                           boolean isDefault, Instant createdAt, Instant updatedAt) {
    }

    // ---------------------------------------------------------------- usta

    record MasterProfileRequest(
            @Schema(example = "Botir Usmonov") @NotBlank @Size(min = 2, max = 120) @PlainText String fullName,
            @Size(max = 2000) @PlainText String bio,
            @NotNull @Min(0) @Max(70) Integer experienceYears,
            @Schema(description = "Ish hududi markazi (verifikatsiyaga yuborish uchun majburiy)") @Valid GeoPoint workLocation,
            @Schema(example = "10", description = "Ish hududi radiusi, km") @NotNull @Min(1) @Max(200) Integer workRadiusKm) {
    }

    record MasterProfileResponse(UUID userId, String fullName, String bio, int experienceYears, String avatarUrl,
                                 GeoPoint workLocation, int workRadiusKm, VerificationStatus verificationStatus,
                                 boolean verified, String rejectionReason, boolean active, BigDecimal ratingAvg,
                                 int ratingCount, Instant submittedAt, Instant verifiedAt, Instant createdAt,
                                 Instant updatedAt) {
    }

    @Schema(description = "Mijozlarga ko'rinadigan usta kartochkasi (aniq ish joyi koordinatasi ko'rsatilmaydi)")
    record PublicMasterResponse(UUID id, String fullName, String bio, int experienceYears, String avatarUrl,
                                int workRadiusKm, BigDecimal ratingAvg, int ratingCount, boolean verified) {
    }

    record DocumentResponse(UUID id, DocumentType type, String contentType, long sizeBytes, String url,
                            Instant createdAt) {
    }

    // ---------------------------------------------------------------- admin

    record AdminMasterResponse(String phone, MasterProfileResponse profile) {
    }

    record AdminMasterDetailResponse(String phone, MasterProfileResponse profile, List<DocumentResponse> documents) {
    }

    record RejectMasterRequest(@NotBlank @Size(max = 1000) @PlainText String reason) {
    }
}
