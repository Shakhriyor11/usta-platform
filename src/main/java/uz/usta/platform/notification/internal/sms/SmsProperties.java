package uz.usta.platform.notification.internal.sms;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.sms")
record SmsProperties(String provider, Eskiz eskiz) {

    /**
     * Eskiz.uz sozlamalari. Eslatma: Eskiz faqat oldindan tasdiqlangan shablon matnlarini yuboradi —
     * {@code sms.otp} matni kabinetda shablon sifatida tasdiqlangan bo'lishi kerak.
     */
    record Eskiz(URI baseUrl, String email, String password, String from, Duration timeout) {
        Eskiz {
            baseUrl = baseUrl == null ? URI.create("https://notify.eskiz.uz") : baseUrl;
            from = from == null ? "4546" : from;
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }
    }
}
