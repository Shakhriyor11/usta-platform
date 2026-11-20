package uz.usta.platform.shared.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.error.CommonErrorCode;

/**
 * Redis asosidagi fixed-window rate limiter (klaster bo'ylab umumiy). Kalitlar: {@code rl:{scope}:{key}}.
 * Limit oshganda 429 va {@code Retry-After} qaytadi.
 */
@Component
public class RateLimiter {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); ttl = tonumber(ARGV[1]) end
            return {c, ttl}
            """, List.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Hisoblagichni oshiradi; {@code limit} dan oshsa {@code TOO_MANY_REQUESTS} tashlaydi.
     *
     * @param scope masalan {@code "otp-request:phone"}
     */
    public void consume(String scope, String key, int limit, Duration window) {
        @SuppressWarnings("unchecked")
        List<Long> result = redis.execute(SCRIPT, List.of("rl:" + scope + ":" + key),
                String.valueOf(window.toMillis()));
        long count = result.get(0);
        long ttlMillis = result.get(1);
        if (count > limit) {
            long retryAfter = Math.max(1, (ttlMillis + 999) / 1000);
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, Map.of("retryAfterSeconds", retryAfter));
        }
    }
}
