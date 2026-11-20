package uz.usta.platform.order.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.order.internal.application.CustomerOrderService;
import uz.usta.platform.order.internal.application.CustomerOrderService.CreateCommand;
import uz.usta.platform.order.internal.application.CustomerOrderService.InlineAddress;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.web.OrderDtos.AttachmentResponse;
import uz.usta.platform.order.internal.web.OrderDtos.CancelRequest;
import uz.usta.platform.order.internal.web.OrderDtos.CreateOrderRequest;
import uz.usta.platform.order.internal.web.OrderDtos.OrderResponse;
import uz.usta.platform.order.internal.web.OrderDtos.OrderSummary;
import uz.usta.platform.order.internal.web.OrderDtos.ReasonRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Customer: Orders", description = "Buyurtma berish va kuzatish (CUSTOMER roli)")
@RestController
@RequestMapping(ApiPaths.CUSTOMER + "/orders")
class CustomerOrderController {

    private final CustomerOrderService service;
    private final OrderWebMapper mapper;

    CustomerOrderController(CustomerOrderService service, OrderWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Buyurtma yaratish",
            description = "Buyurtma darhol SEARCHING holatiga o'tadi va eng yaqin mos ustalarga (standart 5 ta) "
                    + "taklif yuboriladi; birinchi qabul qilgan usta oladi. 15 daqiqada hech kim qabul qilmasa — EXPIRED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateOrderRequest body) {
        InlineAddress address = body.address() == null ? null : new InlineAddress(body.address().address().strip(),
                body.address().details() == null || body.address().details().isBlank() ? null
                        : body.address().details().strip(),
                body.address().location().latitude(), body.address().location().longitude());
        return mapper.toResponse(service.create(user.id(), new CreateCommand(body.serviceId(), body.description(),
                body.addressId(), address, body.scheduledAt(), body.preferredMasterId())));
    }

    @Operation(summary = "Mening buyurtmalarim", description = "?status=SEARCHING&status=ACCEPTED — bir nechta status")
    @GetMapping
    PageResponse<OrderSummary> list(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(required = false) List<OrderStatus> status,
                                    @ParameterObject @PageableDefault(sort = "createdAt",
                                            direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Order> page = service.list(user.id(), status, pageable);
        List<OrderSummary> items = mapper.summaries(page.getContent());
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    @Operation(summary = "Buyurtma tafsilotlari (status tarixi bilan)")
    @GetMapping("/{id}")
    OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return mapper.toResponse(service.get(user.id(), id));
    }

    @Operation(summary = "Bekor qilish", description = "Usta ishni boshlaguncha (CREATED, SEARCHING, ACCEPTED)")
    @PostMapping("/{id}/cancel")
    OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                         @Valid @RequestBody(required = false) CancelRequest body) {
        String reason = body == null || body.reason() == null || body.reason().isBlank() ? null : body.reason().strip();
        return mapper.toResponse(service.cancel(user.id(), id, reason));
    }

    @Operation(summary = "Nizo ochish", description = "IN_PROGRESS, COMPLETED yoki COMMISSION holatida; admin hal qiladi")
    @PostMapping("/{id}/dispute")
    OrderResponse dispute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                          @Valid @RequestBody ReasonRequest body) {
        return mapper.toResponse(service.openDispute(user.id(), id, body.reason().strip()));
    }

    @Operation(summary = "Rasm/video biriktirish (JPEG/PNG/WEBP/MP4, 50MB gacha, 5 tagacha)",
            description = "Usta ishni boshlaguncha")
    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentResponse attach(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                              @RequestPart("file") MultipartFile file) {
        return mapper.toResponse(service.addAttachment(user.id(), id, file));
    }

    @Operation(summary = "Biriktirmani o'chirish")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void detach(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                @PathVariable UUID attachmentId) {
        service.deleteAttachment(user.id(), id, attachmentId);
    }
}
