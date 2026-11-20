package uz.usta.platform.order.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.order.internal.application.AdminOrderService;
import uz.usta.platform.order.internal.domain.Order;
import uz.usta.platform.order.internal.domain.OrderStatus;
import uz.usta.platform.order.internal.web.OrderDtos.AdminOrderResponse;
import uz.usta.platform.order.internal.web.OrderDtos.OrderSummary;
import uz.usta.platform.order.internal.web.OrderDtos.ResolveDisputeRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Admin: Orders", description = "Buyurtmalarni ko'rish va nizolarni hal qilish")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/orders")
class AdminOrderController {

    private final AdminOrderService service;
    private final OrderWebMapper mapper;

    AdminOrderController(AdminOrderService service, OrderWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Buyurtmalar", description = "Nizolar navbati: ?status=DISPUTED&sort=updatedAt,asc")
    @GetMapping
    PageResponse<OrderSummary> list(@RequestParam(required = false) OrderStatus status,
                                    @RequestParam(required = false) UUID customerId,
                                    @RequestParam(required = false) UUID masterId,
                                    @RequestParam(required = false) UUID serviceId,
                                    @ParameterObject @PageableDefault(sort = "createdAt",
                                            direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Order> page = service.search(new AdminOrderService.Filter(status, customerId, masterId, serviceId),
                pageable);
        return new PageResponse<>(mapper.summaries(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Operation(summary = "Buyurtma: tarix, takliflar, komissiya, mijoz ma'lumotlari")
    @GetMapping("/{id}")
    AdminOrderResponse get(@PathVariable UUID id) {
        return mapper.toAdmin(service.get(id));
    }

    @Operation(summary = "Nizoni hal qilish (DISPUTED -> COMPLETED|CANCELLED)")
    @PostMapping("/{id}/resolve")
    AdminOrderResponse resolve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                               @Valid @RequestBody ResolveDisputeRequest body) {
        return mapper.toAdmin(service.resolveDispute(admin.id(), id, body.resolution(), body.price(),
                body.note().strip()));
    }
}
