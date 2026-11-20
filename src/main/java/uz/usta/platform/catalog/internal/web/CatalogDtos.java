package uz.usta.platform.catalog.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.shared.i18n.LocalizedText;

final class CatalogDtos {

    static final String SLUG_REGEX = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private CatalogDtos() {
    }

    // ---------------------------------------------------------------- ko'p tilli matnlar (admin kiritadi)

    @Schema(description = "Ko'p tilli nom: uz majburiy, ru/en ixtiyoriy")
    record NameDto(@Schema(example = "Santexnika") @NotBlank @Size(max = 120) String uz,
                   @Schema(example = "Сантехника") @Size(max = 120) String ru,
                   @Schema(example = "Plumbing") @Size(max = 120) String en) {

        LocalizedText toText() {
            return new LocalizedText(uz.strip(), blankToNull(ru), blankToNull(en));
        }
    }

    record DescriptionDto(@Size(max = 2000) String uz, @Size(max = 2000) String ru, @Size(max = 2000) String en) {

        LocalizedText toText() {
            String u = blankToNull(uz);
            String r = blankToNull(ru);
            String e = blankToNull(en);
            return u == null && r == null && e == null ? null : new LocalizedText(u, r, e);
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // ---------------------------------------------------------------- ommaviy katalog (tanlangan tilda)

    @Schema(description = "Kategoriya daraxti tuguni; name — Accept-Language bo'yicha")
    record CategoryTreeResponse(UUID id, String slug, String name, String icon, List<CategoryTreeResponse> children) {
    }

    record ServiceResponse(UUID id, UUID categoryId, String slug, String name, String description) {
    }

    // ---------------------------------------------------------------- usta xizmatlari va portfolio

    record MasterServiceRequest(
            @Schema(example = "150000", description = "Narx \"dan\", UZS") @NotNull @DecimalMin("0")
            @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal priceFrom,
            @Schema(example = "Materiallar mijozdan") @Size(max = 1000) String description,
            @Schema(description = "false — vaqtincha ko'rsatmaslik (standart: true)") Boolean active) {
    }

    record MasterServiceResponse(UUID serviceId, UUID categoryId, String slug, String name, BigDecimal priceFrom,
                                 String description, boolean active, boolean serviceActive, Instant updatedAt) {
    }

    @Schema(description = "Mijozlarga ko'rinadigan usta xizmati")
    record PublicMasterServiceResponse(UUID serviceId, UUID categoryId, String name, BigDecimal priceFrom,
                                       String description) {
    }

    record PortfolioItemResponse(UUID id, String url, String contentType, long sizeBytes, String description,
                                 Instant createdAt) {
    }

    // ---------------------------------------------------------------- admin

    record CategoryRequest(
            @Schema(description = "Ota kategoriya; null — ildiz") UUID parentId,
            @Schema(example = "plumbing") @NotBlank @Size(max = 80) @Pattern(regexp = SLUG_REGEX) String slug,
            @NotNull @Valid NameDto name,
            @Schema(example = "plumbing") @Size(max = 255) String icon,
            @Schema(example = "10") @Min(0) @Max(100000) Integer sortOrder,
            @Schema(description = "Standart: ACTIVE") CatalogStatus status) {
    }

    record CategoryAdminResponse(UUID id, UUID parentId, String slug, LocalizedText name, String icon, int sortOrder,
                                 CatalogStatus status, Instant createdAt, Instant updatedAt) {
    }

    record ServiceRequest(
            @NotNull UUID categoryId,
            @Schema(example = "faucet-install") @NotBlank @Size(max = 80) @Pattern(regexp = SLUG_REGEX) String slug,
            @NotNull @Valid NameDto name,
            @Valid DescriptionDto description,
            @Min(0) @Max(100000) Integer sortOrder,
            CatalogStatus status) {
    }

    record ServiceAdminResponse(UUID id, UUID categoryId, String slug, LocalizedText name, LocalizedText description,
                                int sortOrder, CatalogStatus status, Instant createdAt, Instant updatedAt) {
    }
}
