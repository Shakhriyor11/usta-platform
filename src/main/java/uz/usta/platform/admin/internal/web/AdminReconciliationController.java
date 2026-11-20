package uz.usta.platform.admin.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.admin.internal.application.ReconciliationService;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.error.CommonErrorCode;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Admin: Finance")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/finance")
class AdminReconciliationController {

    private final ReconciliationService reconciliation;
    private final Clock clock;
    private final ZoneId zone;

    AdminReconciliationController(ReconciliationService reconciliation, Clock clock,
                                  @Value("${app.payment.timezone:Asia/Tashkent}") ZoneId zone) {
        this.reconciliation = reconciliation;
        this.clock = clock;
        this.zone = zone;
    }

    @Operation(summary = "Solishtirish: buyurtma komissiyasi = komissiya hisoboti = ledger",
            description = "?from=2026-11-01&to=2026-11-19 (to — kiritilmaydi). Standart: oxirgi 7 kun")
    @GetMapping("/reconciliation")
    ReconciliationService.Report reconcile(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock.withZone(zone)).plusDays(1) : to;
        LocalDate start = from == null ? end.minusDays(7) : from;
        if (!start.isBefore(end) || start.plusDays(366).isBefore(end)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        return reconciliation.reconcile(start, end);
    }
}
