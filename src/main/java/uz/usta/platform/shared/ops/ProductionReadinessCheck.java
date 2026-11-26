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
 * Production profilida sozlamalarni tekshiradi.
 *
 * <p>Autentifikatsiyani butunlay chetlab o'tishga imkon beradiganlari — namunaviy JWT siri (admin token yasash
 * mumkin) va doimiy OTP kodi (istalgan raqam bilan kirish mumkin) — ilovani ishga tushirmaydi.
 *
 * <p>Qolgan xavfli/lokal sozlamalar (SMS/push stub, Swagger ochiq, standart parollar, localhost CORS/S3 manzili...)
 * WARN logga va {@code app.config.warnings} metrikasiga tushadi — Grafana alert'i (ProductionConfigWarnings) shu
 * metrikaga qaraydi.
 */
@Component
@Profile("prod")
class ProductionReadinessCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionReadinessCheck.class);

    private final Environment env;
    private final List<String> warnings = new ArrayList<>();

    ProductionReadinessCheck(Environment env, MeterRegistry registry) {
        rejectUnsafe(env);
        this.env = env;
        Gauge.builder("app.config.warnings", warnings, List::size)
                .description("Production uchun xavfli sozlamalar soni (0 bo'lishi kerak)").register(registry);
    }

    static void rejectUnsafe(Environment env) {
        if (isPlaceholder(env.getProperty("app.security.jwt.secret", ""))) {
            throw new IllegalStateException("Production: JWT_SECRET is a default/placeholder value — set a random "
                    + "secret (openssl rand -base64 48)");
        }
        if (!env.getProperty("app.identity.otp.fixed-code", "").isBlank()) {
            throw new IllegalStateException("Production: OTP_FIXED_CODE must be empty — it lets anyone sign in "
                    + "as any phone number");
        }
    }

    private static boolean isPlaceholder(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("change-me") || v.contains("insecure") || v.contains("local-dev");
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        warnings.clear();
        String dbPassword = env.getProperty("spring.datasource.password", "");
        String redisPassword = env.getProperty("spring.data.redis.password", "");
        String s3Secret = env.getProperty("app.storage.secret-key", "");
        if (dbPassword.equals("usta") || redisPassword.equals("usta") || s3Secret.equals("usta-minio-secret")
                || isPlaceholder(dbPassword) || isPlaceholder(redisPassword) || isPlaceholder(s3Secret)) {
            warnings.add("Database, Redis or S3 uses a default/placeholder password");
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
        if (env.getProperty("app.identity.super-admin-phones", "").isBlank()) {
            warnings.add("SUPER_ADMIN_PHONES is empty — no way to bootstrap an administrator");
        }
        if (warnings.isEmpty()) {
            log.info("Production readiness check passed");
        } else {
            warnings.forEach(w -> log.warn("PRODUCTION CONFIG WARNING: {}", w));
        }
    }
}
