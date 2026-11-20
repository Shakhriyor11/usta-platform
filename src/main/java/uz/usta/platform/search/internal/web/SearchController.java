package uz.usta.platform.search.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.search.internal.application.MasterSearchService;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;
import uz.usta.platform.storage.FileStorage;

@Tag(name = "Search", description = "Ustalarni geo qidirish va filtrlash")
@RestController
@RequestMapping(ApiPaths.V1 + "/search")
class SearchController {

    private final MasterSearchService search;
    private final FileStorage storage;

    SearchController(MasterSearchService search, FileStorage storage) {
        this.search = search;
        this.storage = storage;
    }

    @Schema(description = "Qidiruv natijasi. priceFrom — so'ralgan xizmat(lar) bo'yicha ustaning eng past \"narx dan\" qiymati")
    record MasterSearchItem(UUID masterId, String fullName, String avatarUrl, int experienceYears,
                            BigDecimal ratingAvg, int ratingCount, BigDecimal distanceKm, BigDecimal priceFrom,
                            boolean online) {
    }

    @Operation(summary = "Ustalarni qidirish",
            description = """
                    serviceId yoki categoryId (bittasi majburiy) va joylashuv: lat+lng yoki addressId.
                    Faqat tasdiqlangan, faol va nuqta o'z ish hududi ichida bo'lgan ustalar qaytadi.
                    Saralash: RATING (standart: reyting ↓, baholar soni ↓, masofa ↑), DISTANCE, PRICE.""")
    @GetMapping("/masters")
    PageResponse<MasterSearchItem> masters(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID categoryId,
            @Parameter(example = "41.311081") @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,
            @Parameter(example = "69.240562") @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Parameter(description = "Mijozning saqlangan manzili (lat/lng o'rniga)") @RequestParam(required = false) UUID addressId,
            @Parameter(description = "km, standart 20, maksimum 100") @RequestParam(required = false) @Min(1) @Max(100) Integer radiusKm,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("5") BigDecimal minRating,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal priceMin,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal priceMax,
            @RequestParam(defaultValue = "false") boolean onlineOnly,
            @RequestParam(defaultValue = "RATING") MasterSearchService.SearchSort sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        MasterSearchService.Result r = search.search(new MasterSearchService.Query(user.id(), serviceId, categoryId,
                lat, lng, addressId, radiusKm, minRating, priceMin, priceMax, onlineOnly, sort, page, size));
        var items = r.hits().stream().map(this::toItem).toList();
        long total = r.total();
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    private MasterSearchItem toItem(MasterSearchService.Hit r) {
        return new MasterSearchItem(r.masterId(), r.fullName(),
                r.avatarKey() == null ? null : storage.url(r.avatarKey()), r.experienceYears(), r.ratingAvg(),
                r.ratingCount(), BigDecimal.valueOf(r.distanceMeters() / 1000d).setScale(1, RoundingMode.HALF_UP),
                r.priceFrom(), r.online());
    }
}
