package uz.usta.platform.shared.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param pollInterval  relay so'rov oralig'i
 * @param batchSize     bir siklda ko'pi bilan nechta xabar
 * @param maxAttempts   shundan keyin xabar "dead" hisoblanadi (qo'lda ko'rib chiqiladi)
 * @param retryBackoff  birinchi qayta urinish kechikishi (har urinishda ikki baravar, 1 soatgacha)
 * @param retention     yetkazilgan xabarlar shuncha vaqtdan keyin o'chiriladi
 */
@ConfigurationProperties("app.outbox")
public record OutboxProperties(boolean relayEnabled, Duration pollInterval, int batchSize, int maxAttempts,
                               Duration retryBackoff, Duration retention) {

    public OutboxProperties {
        pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        batchSize = batchSize <= 0 ? 100 : batchSize;
        maxAttempts = maxAttempts <= 0 ? 10 : maxAttempts;
        retryBackoff = retryBackoff == null ? Duration.ofSeconds(10) : retryBackoff;
        retention = retention == null ? Duration.ofDays(7) : retention;
    }
}
