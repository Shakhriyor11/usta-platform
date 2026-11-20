package uz.usta.platform.review.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.profile.CustomerDirectory;
import uz.usta.platform.review.internal.application.ReviewService;
import uz.usta.platform.review.internal.domain.Review;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Reviews", description = "Baho va sharhlar")
@RestController
class ReviewController {

    private final ReviewService service;
    private final CustomerDirectory customers;

    ReviewController(ReviewService service, CustomerDirectory customers) {
        this.service = service;
        this.customers = customers;
    }

    record ReviewRequest(@Schema(example = "5") @NotNull @Min(1) @Max(5) Integer rating,
                         @Schema(example = "Tez va sifatli ishladi") @Size(max = 2000) String comment) {
    }

    record ReviewResponse(UUID id, UUID orderId, UUID masterId, int rating, String comment, Instant createdAt) {
    }

    @Schema(description = "Ommaviy sharh: mijoz ismi qisqartirilgan (\"Aziz K.\")")
    record PublicReviewResponse(UUID id, String authorName, int rating, String comment, Instant createdAt) {
    }

    @Operation(summary = "Buyurtmani baholash (1–5)",
            description = "Faqat buyurtma egasi, ish yakunlangandan keyin, bir marta. Ustaning reytingi darhol yangilanadi.")
    @PostMapping(ApiPaths.CUSTOMER + "/orders/{orderId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    ReviewResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId,
                          @Valid @RequestBody ReviewRequest body) {
        return toResponse(service.create(user.id(), orderId, body.rating(), body.comment()));
    }

    @Operation(summary = "Buyurtmaga qo'ygan bahom")
    @GetMapping(ApiPaths.CUSTOMER + "/orders/{orderId}/review")
    ReviewResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId) {
        return toResponse(service.forOrder(user.id(), orderId));
    }

    @Operation(summary = "Usta sharhlari")
    @GetMapping(ApiPaths.V1 + "/masters/{masterId}/reviews")
    PageResponse<PublicReviewResponse> forMaster(@PathVariable UUID masterId,
                                                 @ParameterObject @PageableDefault(sort = "createdAt",
                                                         direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Review> page = service.forMaster(masterId, pageable);
        Map<UUID, String> names = customers.names(page.map(Review::getCustomerId).toSet());
        List<PublicReviewResponse> items = page.getContent().stream()
                .map(r -> new PublicReviewResponse(r.getId(), shortName(names.get(r.getCustomerId())), r.getRating(),
                        r.getComment(), r.getCreatedAt()))
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    private static ReviewResponse toResponse(Review r) {
        return new ReviewResponse(r.getId(), r.getOrderId(), r.getMasterId(), r.getRating(), r.getComment(),
                r.getCreatedAt());
    }

    /** "Aziz Karimov" -> "Aziz K."; ism yo'q bo'lsa — null (ilova "Mijoz" deb ko'rsatadi). */
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.strip().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[1].charAt(0) + ".";
    }
}
