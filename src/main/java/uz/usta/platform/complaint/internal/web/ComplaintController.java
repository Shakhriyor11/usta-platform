package uz.usta.platform.complaint.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.complaint.internal.application.ComplaintService;
import uz.usta.platform.complaint.internal.domain.Complaint;
import uz.usta.platform.complaint.internal.domain.ComplaintReason;
import uz.usta.platform.complaint.internal.domain.ComplaintStatus;
import uz.usta.platform.order.OrderApi.DisputeResolution;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.validation.PlainText;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@RestController
class ComplaintController {

    private final ComplaintService service;

    ComplaintController(ComplaintService service) {
        this.service = service;
    }

    record FileRequest(@NotNull UUID orderId, @NotNull ComplaintReason reason,
                       @Schema(example = "Usta kelishilgan vaqtda kelmadi") @NotBlank @Size(max = 2000) @PlainText String description,
                       @Schema(description = "true — buyurtma bo'yicha nizo ochish (IN_PROGRESS/COMPLETED/COMMISSION)")
                       Boolean dispute) {
    }

    record CloseRequest(@NotNull Result result, @NotBlank @Size(max = 2000) @PlainText String note,
                        @Schema(description = "Nizo ochiq bo'lsa majburiy") DisputeResolution orderResolution,
                        @Schema(description = "orderResolution=COMPLETED va narx kiritilmagan bo'lsa")
                        @DecimalMin("1") @Digits(integer = 12, fraction = 2) BigDecimal price) {
        enum Result { RESOLVED, REJECTED }
    }

    record ComplaintResponse(UUID id, UUID orderId, UUID authorId, UUID againstUserId, ComplaintReason reason,
                             String description, boolean dispute, ComplaintStatus status, String resolutionNote,
                             Instant resolvedAt, Instant createdAt) {
        static ComplaintResponse of(Complaint c) {
            return new ComplaintResponse(c.getId(), c.getOrderId(), c.getAuthorId(), c.getAgainstUserId(),
                    c.getReason(), c.getDescription(), c.isDispute(), c.getStatus(), c.getResolutionNote(),
                    c.getResolvedAt(), c.getCreatedAt());
        }
    }

    record AdminComplaintResponse(ComplaintResponse complaint, String orderStatus, UUID assignedTo,
                                  @Schema(description = "Shu foydalanuvchiga nisbatan jami shikoyatlar")
                                  long complaintsAgainstUser) {
    }

    // ---------------------------------------------------------------- foydalanuvchi

    @Tag(name = "Complaints", description = "Shikoyatlar (mijoz yoki usta)")
    @Operation(summary = "Shikoyat yuborish", description = "Usta biriktirilgan buyurtma bo'yicha; ikkinchi tomonga nisbatan")
    @PostMapping(ApiPaths.V1 + "/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    ComplaintResponse file(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody FileRequest body) {
        return ComplaintResponse.of(service.file(user.id(), body.orderId(), body.reason(), body.description(),
                Boolean.TRUE.equals(body.dispute())));
    }

    @Tag(name = "Complaints")
    @Operation(summary = "Mening shikoyatlarim")
    @GetMapping(ApiPaths.V1 + "/complaints")
    PageResponse<ComplaintResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                         @ParameterObject @PageableDefault(sort = "createdAt",
                                                 direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.mine(user.id(), pageable), ComplaintResponse::of);
    }

    @Tag(name = "Complaints")
    @Operation(summary = "Shikoyat holati")
    @GetMapping(ApiPaths.V1 + "/complaints/{id}")
    ComplaintResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ComplaintResponse.of(service.mine(user.id(), id));
    }

    // ---------------------------------------------------------------- admin

    @Tag(name = "Admin: Complaints", description = "Shikoyatlar va nizolarni ko'rib chiqish")
    @Operation(summary = "Shikoyatlar", description = "Navbat: ?status=OPEN&sort=createdAt,asc")
    @GetMapping(ApiPaths.ADMIN + "/complaints")
    PageResponse<ComplaintResponse> list(@RequestParam(required = false) ComplaintStatus status,
                                         @RequestParam(required = false) UUID orderId,
                                         @RequestParam(required = false) UUID againstUserId,
                                         @RequestParam(required = false) UUID authorId,
                                         @ParameterObject @PageableDefault(sort = "createdAt",
                                                 direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(new ComplaintService.Filter(status, orderId, againstUserId, authorId),
                pageable), ComplaintResponse::of);
    }

    @Tag(name = "Admin: Complaints")
    @Operation(summary = "Shikoyat tafsilotlari", description = "Buyurtma chati: GET /api/v1/admin/orders/{orderId}/chat/messages")
    @GetMapping(ApiPaths.ADMIN + "/complaints/{id}")
    AdminComplaintResponse adminGet(@PathVariable UUID id) {
        return toAdmin(service.adminView(id));
    }

    @Tag(name = "Admin: Complaints")
    @Operation(summary = "Ko'rib chiqishga olish (OPEN -> IN_REVIEW)")
    @PostMapping(ApiPaths.ADMIN + "/complaints/{id}/take")
    AdminComplaintResponse take(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        service.take(admin.id(), id);
        return toAdmin(service.adminView(id));
    }

    @Tag(name = "Admin: Complaints")
    @Operation(summary = "Yopish (RESOLVED | REJECTED)", description = "Nizo ochiq bo'lsa — orderResolution bilan buyurtma bo'yicha ham qaror")
    @PostMapping(ApiPaths.ADMIN + "/complaints/{id}/close")
    AdminComplaintResponse close(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                 @Valid @RequestBody CloseRequest body) {
        service.close(admin.id(), id, ComplaintStatus.valueOf(body.result().name()), body.note().strip(),
                body.orderResolution(), body.price());
        return toAdmin(service.adminView(id));
    }

    private static AdminComplaintResponse toAdmin(ComplaintService.AdminView v) {
        return new AdminComplaintResponse(ComplaintResponse.of(v.complaint()), v.orderStatus(),
                v.complaint().getAssignedTo(), v.complaintsAgainstUser());
    }
}
