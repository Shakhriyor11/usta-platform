package uz.usta.platform.admin.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.admin.internal.application.OutboxAdminService;
import uz.usta.platform.shared.outbox.OutboxDeadLetters;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Admin: Outbox", description = "Yetkazilmay qolgan (dead) eventlar")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/outbox")
class AdminOutboxController {

    private final OutboxAdminService service;

    AdminOutboxController(OutboxAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Urinishlari tugagan eventlar (eng eskisidan)")
    @GetMapping("/dead")
    List<OutboxDeadLetters.DeadEvent> dead(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return service.dead(limit);
    }

    @Operation(summary = "Hammasini qayta navbatga qo'yish", description = "Xato sababi bartaraf etilgandan keyin")
    @PostMapping("/dead/retry")
    Map<String, Integer> retry(@AuthenticationPrincipal AuthenticatedUser admin) {
        return Map.of("requeued", service.retryDead(admin.id()));
    }
}
