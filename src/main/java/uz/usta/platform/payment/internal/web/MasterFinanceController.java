package uz.usta.platform.payment.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.payment.internal.application.FinanceQueryService;
import uz.usta.platform.payment.internal.domain.CommissionStatus;
import uz.usta.platform.payment.internal.web.FinanceDtos.CommissionResponse;
import uz.usta.platform.payment.internal.web.FinanceDtos.SummaryResponse;
import uz.usta.platform.payment.internal.web.FinanceDtos.TransactionResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Master: Finance", description = "Komissiya va hisob (MASTER roli)")
@RestController
@RequestMapping(ApiPaths.MASTER + "/finance")
class MasterFinanceController {

    private final FinanceQueryService finance;
    private final Clock clock;

    MasterFinanceController(FinanceQueryService finance, Clock clock) {
        this.finance = finance;
        this.clock = clock;
    }

    @Operation(summary = "Hisob holati: balans, to'lanishi kerak, joriy hafta")
    @GetMapping("/summary")
    SummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
        var s = finance.summary(user.id(), Instant.now(clock));
        return new SummaryResponse(s.balance(), s.invoicedDue(), s.accrued(), s.currentPeriod(), "UZS");
    }

    @Operation(summary = "Buyurtmalar bo'yicha komissiyalar", description = "?period=2026-11-16&status=INVOICED")
    @GetMapping("/commissions")
    PageResponse<CommissionResponse> commissions(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam(required = false) LocalDate period,
                                                 @RequestParam(required = false) CommissionStatus status,
                                                 @ParameterObject @PageableDefault(sort = "createdAt",
                                                         direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(finance.commissions(new FinanceQueryService.Filter(user.id(), period, status), pageable),
                CommissionResponse::of);
    }

    @Operation(summary = "Hisob yozuvlari (ledger)")
    @GetMapping("/transactions")
    PageResponse<TransactionResponse> transactions(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @ParameterObject @PageableDefault(sort = "createdAt",
                                                           direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(finance.transactions(user.id(), pageable), TransactionResponse::of);
    }
}
