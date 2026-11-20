package uz.usta.platform.notification.internal.sms;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uz.usta.platform.notification.SmsDeliveryException;
import uz.usta.platform.notification.SmsSender;

/**
 * Eskiz.uz adapteri: {@code POST /api/auth/login} bilan token olinadi (keshlanadi), so'ng
 * {@code POST /api/message/sms/send}. Token muddati o'tib 401 qaytsa — bir marta qayta login qilinadi.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "eskiz")
class EskizSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(EskizSmsSender.class);

    private final RestClient http;
    private final SmsProperties.Eskiz cfg;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String token;

    EskizSmsSender(SmsProperties props) {
        this.cfg = props.eskiz();
        if (cfg == null || cfg.email() == null || cfg.password() == null) {
            throw new IllegalStateException("app.sms.eskiz.email/password (ESKIZ_EMAIL/ESKIZ_PASSWORD) are required");
        }
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(cfg.timeout());
        rf.setReadTimeout(cfg.timeout());
        this.http = RestClient.builder().baseUrl(cfg.baseUrl().toString()).requestFactory(rf).build();
    }

    @Override
    public void send(String phone, String text) {
        try {
            doSend(currentToken(), phone, text);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                token = null;
                try {
                    doSend(currentToken(), phone, text);
                    return;
                } catch (RestClientException retry) {
                    throw failure(retry);
                }
            }
            throw failure(e);
        } catch (RestClientException e) {
            throw failure(e);
        }
    }

    private void doSend(String bearer, String phone, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile_phone", phone.startsWith("+") ? phone.substring(1) : phone);
        form.add("message", text);
        form.add("from", cfg.from());
        http.post().uri("/api/message/sms/send")
                .headers(h -> h.setBearerAuth(bearer))
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private String currentToken() {
        String t = token;
        if (t != null) {
            return t;
        }
        lock.lock();
        try {
            if (token == null) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("email", cfg.email());
                form.add("password", cfg.password());
                @SuppressWarnings("unchecked")
                Map<String, Object> body = http.post().uri("/api/auth/login").body(form).retrieve().body(Map.class);
                Object data = body == null ? null : body.get("data");
                if (!(data instanceof Map<?, ?> d) || !(d.get("token") instanceof String tok)) {
                    throw new SmsDeliveryException("Eskiz login response has no token", null);
                }
                token = tok;
            }
            return token;
        } finally {
            lock.unlock();
        }
    }

    private static SmsDeliveryException failure(RestClientException e) {
        log.error("Eskiz SMS delivery failed: {}", e.getMessage());
        return new SmsDeliveryException("Eskiz SMS delivery failed", e);
    }
}
