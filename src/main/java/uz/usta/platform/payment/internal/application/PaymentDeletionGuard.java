package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.usta.platform.identity.UserDeletionGuard;
import uz.usta.platform.payment.internal.domain.PaymentErrorCode;
import uz.usta.platform.payment.internal.persistence.LedgerRepository;
import uz.usta.platform.shared.error.BusinessException;

/** Komissiya qarzi bor usta hisobi o'chirilmaydi (qarz egasiz qolib ketmasin). */
@Component
class PaymentDeletionGuard implements UserDeletionGuard {

    private final LedgerRepository ledger;
    private final MasterAccount account;

    PaymentDeletionGuard(LedgerRepository ledger, MasterAccount account) {
        this.ledger = ledger;
        this.account = account;
    }

    @Override
    public void requireDeletable(UUID userId) {
        account.lock(userId); // parallel komissiya hisoblanishi tekshiruvdan o'tib ketmasin
        BigDecimal balance = ledger.balance(userId);
        if (balance.signum() < 0) {
            BigDecimal debt = balance.negate();
            throw new BusinessException(PaymentErrorCode.USER_HAS_DEBT, Map.of("debt", debt), debt.toPlainString());
        }
    }
}
