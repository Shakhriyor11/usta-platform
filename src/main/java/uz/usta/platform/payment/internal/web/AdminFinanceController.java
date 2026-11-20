package uz.usta.platform.payment.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.payment.internal.application.FinanceQueryService;
import uz.usta.platform.payment.internal.application.InvoicingService;
import uz.usta.platform.payment.internal.application.SettlementService;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.web.FinanceDtos.CommissionResponse;
import uz.usta.platform.payment.internal.web.FinanceDtos.SettleRequest;
import uz.usta.platform.payment.internal.web.FinanceDtos.SettlementResponse;
import uz.usta.platform.payment.internal.web.FinanceDtos.TransactionResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Admin: Finance", description = "Komissiyalar, haftalik hisob-fakturalar, ustalar to'lovlari")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/finance")
class AdminFinanceController {

    private final FinanceQueryService finance;
    private final SettlementService settlements;
    private final InvoicingService invoicing;

    AdminFinanceController(FinanceQueryService finance, SettlementService settlements, InvoicingService invoicing) {
        this.finance = finance;
        this.settlements = settlements;
        this.invoicing = invoicing;
    }

    @Operation(summary = "Hisob-fakturalar: usta × hafta bo'yicha yig'ma", description = "?period=2026-11-16 yoki ?masterId=")
    @GetMapping("/invoices")
    List<FinanceQueryService.Invoice> invoices(@RequestParam(required = false) LocalDate period,
                                               @RequestParam(required = false) UUID masterId) {
        return finance.invoices(period, masterId);
    }

    @Operation(summary = "Komissiyalar (buyurtmalar bo'yicha)")
    @GetMapping("/commissions")
    PageResponse<CommissionResponse> commissions(@RequestParam(required = false) UUID masterId,
                                                 @RequestParam(required = false) LocalDate period,
                                                 @RequestParam(required = false) CommissionStatus status,
                                                 @ParameterObject @PageableDefault(sort = "createdAt",
                                                         direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(finance.commissions(new FinanceQueryService.Filter(masterId, period, status), pageable),
                CommissionResponse::of);
    }

    @Operation(summary = "Usta hisobi (ledger)")
    @GetMapping("/transactions")
    PageResponse<TransactionResponse> transactions(@RequestParam UUID masterId,
                                                   @ParameterObject @PageableDefault(sort = "createdAt",
                                                           direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(finance.transactions(masterId, pageable), TransactionResponse::of);
    }

    @Operation(summary = "Ustaning komissiya to'lovini qabul qilish",
            description = "Summa yopilgan hafta (INVOICED) bo'yicha to'lanmagan summaga teng bo'lishi shart")
    @PostMapping("/settlements")
    SettlementResponse settle(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody SettleRequest body) {
        var s = settlements.settle(admin.id(), body.masterId(), body.period(), body.amount(), body.reference());
        return new SettlementResponse(s.masterId(), s.period(), s.amount(), s.orders(), s.balance());
    }

    @Operation(summary = "Tugagan haftalarni hozir yopish", description = "Odatda avtomatik (har soat). Idempotent")
    @PostMapping("/invoices/close")
    Map<String, Integer> close() {
        return Map.of("invoiced", invoicing.closeEndedPeriods());
    }
}
