package uz.usta.platform.shared.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Standart dispatcher: xabarni Spring event sifatida e'lon qiladi. Modullar quyidagicha tinglaydi:
 * <pre>{@code
 * @EventListener(condition = "#m.eventType == 'order.status-changed'")
 * void on(OutboxMessage m) { ... }
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
class ApplicationEventOutboxDispatcher {

    @Bean
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    OutboxDispatcher applicationEventDispatcher(ApplicationEventPublisher publisher) {
        return publisher::publishEvent;
    }
}
