package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.usta.platform.payment.internal.domain.PaymentMethod;
import uz.usta.platform.payment.internal.domain.PaymentStatus;

/** Naqd: pulni usta oladi, platforma faqat qayd etadi. */
@Component
class CashGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CASH;
    }

    @Override
    public String provider() {
        return "cash";
    }

    @Override
    public Result capture(UUID orderId, BigDecimal amount) {
        return new Result(PaymentStatus.PAID, null);
    }
}
