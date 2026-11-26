package uz.usta.platform.shared.security;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Bloklangan foydalanuvchilarning hali amal qilayotgan access tokenlarini darhol bekor qilish.
 * Access token stateless (15 daq) — bloklashda refresh tokenlar bekor bo'ladi, lekin access token muddati
 * tugaguncha ishlardi. Shuning uchun bloklashda {@code revoked:user:{id}} kaliti access token TTL'iga teng
 * muddatga yoziladi va har autentifikatsiyalangan so'rovda (REST + WebSocket CONNECT) tekshiriladi.
 * Redis ishlamasa — tekshiruv o'tkazib yuboriladi (fail-open): tokenlar baribir qisqa muddatli.
 */
@Component
public class UserRevocations {

    private static final Logger log = LoggerFactory.getLogger(UserRevocations.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    UserRevocations(StringRedisTemplate redis, JwtProperties jwt) {
        this.redis = redis;
        this.ttl = jwt.accessTokenTtl().plusMinutes(1); // soat farqi uchun zaxira
    }

    public void revoke(UUID userId) {
        redis.opsForValue().set(key(userId), "1", ttl);
    }

    public void restore(UUID userId) {
        redis.delete(key(userId));
    }

    public boolean isRevoked(UUID userId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(userId)));
        } catch (RuntimeException e) {
            log.warn("Revocation check skipped (Redis unavailable): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Rol yoki huquqlar o'zgardi: eski versiyali access tokenlar darhol rad etiladi (401) va mijoz refresh orqali
     * yangi huquqlar bilan token oladi.
     */
    public void authzChanged(UUID userId, int version) {
        redis.opsForValue().set(versionKey(userId), Integer.toString(version), ttl);
    }

    public boolean isOutdated(UUID userId, int tokenVersion) {
        try {
            String current = redis.opsForValue().get(versionKey(userId));
            return current != null && tokenVersion < Integer.parseInt(current);
        } catch (RuntimeException e) {
            log.warn("Authorization version check skipped (Redis unavailable): {}", e.getMessage());
            return false;
        }
    }

    private static String key(UUID userId) {
        return "revoked:user:" + userId;
    }

    private static String versionKey(UUID userId) {
        return "authz:ver:" + userId;
    }
}
