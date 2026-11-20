package uz.usta.platform.shared.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS sozlamalari — ruxsat etilgan origin'lar faqat environment orqali beriladi. */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins, Duration maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }
}
