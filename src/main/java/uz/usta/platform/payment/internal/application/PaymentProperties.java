package uz.usta.platform.payment.internal.application;

import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param commissionPercent platforma komissiyasi, foiz (0..100), standart 10
 * @param timezone          hisob-kitob davrlari (hafta, dushanbadan) shu vaqt zonasida, standart Asia/Tashkent
 * @param invoiceDueDays    davr yopilgandan keyin komissiyani to'lash muddati, kun
 */
@ConfigurationProperties("app.payment")
public record PaymentProperties(BigDecimal commissionPercent, ZoneId timezone, int invoiceDueDays) {

    public PaymentProperties {
        if (commissionPercent == null) {
            commissionPercent = BigDecimal.TEN;
        }
        if (commissionPercent.signum() < 0 || commissionPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("app.payment.commission-percent must be 0..100");
        }
        timezone = timezone == null ? ZoneId.of("Asia/Tashkent") : timezone;
        invoiceDueDays = invoiceDueDays <= 0 ? 7 : invoiceDueDays;
    }
}
