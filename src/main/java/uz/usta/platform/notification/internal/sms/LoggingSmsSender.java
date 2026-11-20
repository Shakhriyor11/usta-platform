package uz.usta.platform.notification.internal.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import uz.usta.platform.notification.SmsSender;

/**
 * Lokal ishlab chiqish uchun: SMS yubormaydi, matnni logga yozadi (OTP kodini
 * {@code docker compose logs api} dan ko'rish mumkin). PRODUCTION'da ishlatmang.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    LoggingSmsSender(Environment env) {
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            log.warn("SMS provider is 'log' in prod profile — SMS messages are NOT delivered. Set SMS_PROVIDER=eskiz");
        }
    }

    @Override
    public void send(String phone, String text) {
        log.info("[SMS stub] to={} text=\"{}\"", phone, text);
    }
}
