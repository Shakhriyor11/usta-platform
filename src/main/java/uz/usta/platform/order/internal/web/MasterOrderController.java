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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.order.internal.application.MasterOrderService;
import uz.usta.platform.order.internal.application.MasterOrderService.MasterView;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.web.OrderDtos.CompleteRequest;
import uz.usta.platform.order.internal.web.OrderDtos.MasterOfferResponse;
import uz.usta.platform.order.internal.web.OrderDtos.MasterOrderResponse;
import uz.usta.platform.order.internal.web.OrderDtos.OrderSummary;
import uz.usta.platform.order.internal.web.OrderDtos.ReasonRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Master: Orders", description = "Takliflar va buyurtmalarni bajarish (MASTER roli)")
@RestController
@RequestMapping(ApiPaths.MASTER)
class MasterOrderController {

    private final MasterOrderService service;
    private final OrderWebMapper mapper;

    MasterOrderController(MasterOrderService service, OrderWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Ochiq takliflar (javob kutilayotgan)")
    @GetMapping("/offers")
    List<MasterOfferResponse> offers(@AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.offers(service.openOffers(user.id()));
    }

    @Operation(summary = "Taklifni qabul qilish",
            description = "Birinchi qabul qilgan usta oladi; kech qolganlar 409 ORDER_ALREADY_TAKEN oladi")
    @PostMapping("/offers/{orderId}/accept")
    MasterOrderResponse accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId) {
        return mapper.toMasterResponse(new MasterView(service.accept(user.id(), orderId), null));
    }

    @Operation(summary = "Taklifni rad etish", description = "Buyurtma keyingi ustalarga o'tadi")
    @PostMapping("/offers/{orderId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID orderId) {
        service.reject(user.id(), orderId);
    }

    @Operation(summary = "Menga biriktirilgan buyurtmalar")
    @GetMapping("/orders")
    PageResponse<OrderSummary> orders(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(required = false) List<OrderStatus> status,
                                      @ParameterObject @PageableDefault(sort = "createdAt",
                                              direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Order> page = service.myOrders(user.id(), status, pageable);
        return new PageResponse<>(mapper.summaries(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Operation(summary = "Buyurtma tafsilotlari", description = "Biriktirilgan yoki taklif yuborilgan buyurtma")
    @GetMapping("/orders/{id}")
    MasterOrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return mapper.toMasterResponse(service.view(user.id(), id));
    }

    @Operation(summary = "Ishni boshlash (ACCEPTED -> IN_PROGRESS)")
    @PostMapping("/orders/{id}/start")
    MasterOrderResponse start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return mapper.toMasterResponse(new MasterView(service.start(user.id(), id), null));
    }

    @Operation(summary = "Ishni yakunlash (IN_PROGRESS -> COMPLETED -> COMMISSION)",
            description = "Yakuniy narx kiritiladi, platforma komissiyasi hisoblanadi")
    @PostMapping("/orders/{id}/complete")
    MasterOrderResponse complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                 @Valid @RequestBody CompleteRequest body) {
        return mapper.toMasterResponse(new MasterView(service.complete(user.id(), id, body.price()), null));
    }

    @Operation(summary = "Qabul qilingan buyurtmadan voz kechish (faqat ACCEPTED, sabab majburiy)",
            description = "Buyurtma bekor bo'lmaydi: qayta qidiruvga (SEARCHING) o'tadi va boshqa ustalarga yuboriladi")
    @PostMapping("/orders/{id}/cancel")
    MasterOrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                               @Valid @RequestBody ReasonRequest body) {
        return mapper.toMasterResponse(service.cancel(user.id(), id, body.reason().strip()));
    }

    @Operation(summary = "Nizo ochish")
    @PostMapping("/orders/{id}/dispute")
    MasterOrderResponse dispute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                @Valid @RequestBody ReasonRequest body) {
        return mapper.toMasterResponse(new MasterView(service.openDispute(user.id(), id, body.reason().strip()), null));
    }
}
