package uz.usta.platform.shared.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Production profilida ishga tushganda xavfli/lokal sozlamalarni tekshiradi (standart JWT siri, SMS/push stub,
 * Swagger ochiq, localhost CORS/S3 manzili...). Har biri WARN logga va {@code app.config.warnings} metrikasiga
 * tushadi — Grafana alert'i (ProductionConfigWarnings) shu metrikaga qaraydi. Ilovani to'xtatmaydi:
 * {@code docker compose up -d} lokal standartlar bilan ham ishga tushishi kerak.
 */
@Component
@Profile("prod")
class ProductionReadinessCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionReadinessCheck.class);

    private final Environment env;
    private final List<String> warnings = new ArrayList<>();

    ProductionReadinessCheck(Environment env, MeterRegistry registry) {
        this.env = env;
        Gauge.builder("app.config.warnings", warnings, List::size)
                .description("Production uchun xavfli sozlamalar soni (0 bo'lishi kerak)").register(registry);
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        warnings.clear();
        String secret = env.getProperty("app.security.jwt.secret", "").toLowerCase(Locale.ROOT);
        if (secret.contains("change-me") || secret.contains("insecure") || secret.contains("local-dev")) {
            warnings.add("JWT_SECRET is a default/placeholder value — set a random secret (openssl rand -base64 48)");
        }
        if ("log".equals(env.getProperty("app.sms.provider", "log"))) {
            warnings.add("SMS_PROVIDER=log — OTP codes are written to logs and NOT delivered");
        }
        if ("log".equals(env.getProperty("app.push.provider", "log"))) {
            warnings.add("PUSH_PROVIDER=log — push notifications are NOT delivered");
        }
        if (env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
            warnings.add("SWAGGER_ENABLED=true — API documentation is publicly accessible");
        }
        if (env.getProperty("app.cors.allowed-origins", "").contains("localhost")) {
            warnings.add("CORS_ALLOWED_ORIGINS contains localhost");
        }
        String s3 = env.getProperty("app.storage.public-endpoint", "");
        if (s3.contains("localhost") || s3.contains("127.0.0.1")) {
            warnings.add("S3_PUBLIC_ENDPOINT points to localhost — file URLs will not open on clients");
        }
        if (env.getProperty("app.identity.admin-phones", "").isBlank()) {
            warnings.add("ADMIN_PHONES is empty — no way to bootstrap an administrator");
        }
        if (warnings.isEmpty()) {
            log.info("Production readiness check passed");
        } else {
            warnings.forEach(w -> log.warn("PRODUCTION CONFIG WARNING: {}", w));
        }
    }
}
