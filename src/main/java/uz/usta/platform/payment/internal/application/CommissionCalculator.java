package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Komissiya summasi: narx × foiz, tiyingacha (2 xona) HALF_UP. Barcha kategoriyalar uchun bir xil foiz. */
@Component
public class CommissionCalculator {

    private final BigDecimal percent;

    public CommissionCalculator(PaymentProperties props) {
        this.percent = props.commissionPercent();
    }

    public BigDecimal percent() {
        return percent;
    }

    public BigDecimal calculate(BigDecimal price) {
        return price.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
