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
import uz.usta.platform.shared.validation.PlainText;

final class CatalogDtos {

    static final String SLUG_REGEX = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private static final String ICON_URL =
            "Yuklangan ikonkaning vaqtinchalik havolasi yoki to'plam SVG'sining nisbiy yo'li";
    private static final String IMAGE_URL = "Rasmning vaqtinchalik (15 daq) havolasi";

    private CatalogDtos() {
    }

    // ---------------------------------------------------------------- ko'p tilli matnlar (admin kiritadi)

    @Schema(description = "Ko'p tilli nom: uz, ru, en majburiy; uzCyrl bo'sh bo'lsa lotinchadan o'giriladi")
    record NameDto(@Schema(example = "Santexnika") @NotBlank @Size(min = 2, max = 120) @PlainText String uz,
                   @Schema(example = "Сантехника") @NotBlank @Size(min = 2, max = 120) @PlainText String ru,
                   @Schema(example = "Plumbing") @NotBlank @Size(min = 2, max = 120) @PlainText String en,
                   @Schema(example = "Сантехника") @Size(max = 120) @PlainText String uzCyrl) {

        LocalizedText toText() {
            return new LocalizedText(uz.strip(), ru.strip(), en.strip(), blankToNull(uzCyrl));
        }
    }

    record DescriptionDto(@Size(max = 2000) @PlainText String uz, @Size(max = 2000) @PlainText String ru,
                          @Size(max = 2000) @PlainText String en, @Size(max = 2000) @PlainText String uzCyrl) {

        LocalizedText toText() {
            String u = blankToNull(uz);
            String r = blankToNull(ru);
            String e = blankToNull(en);
            String c = blankToNull(uzCyrl);
            return u == null && r == null && e == null && c == null ? null : new LocalizedText(u, r, e, c);
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // ---------------------------------------------------------------- ommaviy katalog (tanlangan tilda)

    @Schema(description = "Kategoriya daraxti tuguni; name — Accept-Language bo'yicha")
    record CategoryTreeResponse(UUID id, String slug, String name,
                                @Schema(description = "To'plamdagi ikonka kaliti") String icon,
                                @Schema(description = ICON_URL) String iconUrl,
                                @Schema(description = IMAGE_URL) String imageUrl,
                                List<CategoryTreeResponse> children) {
    }

    record ServiceResponse(UUID id, UUID categoryId, String slug, String name, String description, String icon,
                           @Schema(description = ICON_URL) String iconUrl,
                           @Schema(description = IMAGE_URL) String imageUrl) {
    }

    @Schema(description = "Tayyor to'plamdagi ikonka; url — SVG fayl")
    record IconResponse(String key, String name, String url) {
    }

    // ---------------------------------------------------------------- usta xizmatlari va portfolio

    record MasterServiceRequest(
            @Schema(example = "150000", description = "Narx \"dan\", UZS") @NotNull @DecimalMin("0")
            @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal priceFrom,
            @Schema(example = "Materiallar mijozdan") @Size(max = 1000) @PlainText String description,
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
            @Schema(example = "plumbing", description = "GET /public/catalog/icons dagi kalit; yuklangan ikonkani "
                    + "almashtiradi. null — yuklangan ikonka o'zgarmaydi") @Size(max = 255) String icon,
            @Schema(example = "10") @Min(0) @Max(100000) Integer sortOrder,
            @Schema(description = "Standart: ACTIVE") CatalogStatus status) {
    }

    record CategoryAdminResponse(UUID id, UUID parentId, String slug, LocalizedText name,
                                 @Schema(description = "To'plamdagi ikonka kaliti; yuklangan ikonkada null") String icon,
                                 @Schema(description = ICON_URL) String iconUrl,
                                 @Schema(description = "Ikonka admin yuklagan faylmi") boolean customIcon,
                                 @Schema(description = IMAGE_URL) String imageUrl, int sortOrder, CatalogStatus status,
                                 Instant createdAt, Instant updatedAt) {
    }

    record ServiceRequest(
            @NotNull UUID categoryId,
            @Schema(example = "faucet-install") @NotBlank @Size(max = 80) @Pattern(regexp = SLUG_REGEX) String slug,
            @NotNull @Valid NameDto name,
            @Valid DescriptionDto description,
            @Schema(example = "toilet", description = "CategoryRequest.icon kabi") @Size(max = 255) String icon,
            @Min(0) @Max(100000) Integer sortOrder,
            CatalogStatus status) {
    }

    record ServiceAdminResponse(UUID id, UUID categoryId, String slug, LocalizedText name, LocalizedText description,
                                @Schema(description = "To'plamdagi ikonka kaliti; yuklangan ikonkada null") String icon,
                                @Schema(description = ICON_URL) String iconUrl,
                                @Schema(description = "Ikonka admin yuklagan faylmi") boolean customIcon,
                                @Schema(description = IMAGE_URL) String imageUrl, int sortOrder,
                                CatalogStatus status, Instant createdAt, Instant updatedAt) {
    }
}
