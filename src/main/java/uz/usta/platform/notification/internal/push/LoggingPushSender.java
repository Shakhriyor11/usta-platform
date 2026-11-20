package uz.usta.platform.notification.internal.push;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Lokal/test: push yubormaydi, logga yozadi. */
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "log", matchIfMissing = true)
class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(String token, String title, String body, Map<String, String> data) {
        log.info("[Push stub] token={}… title=\"{}\" body=\"{}\" data={}",
                token.substring(0, Math.min(8, token.length())), title, body, data);
    }
}
