package uz.usta.platform.identity.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import uz.usta.platform.identity.OtpDelivery;
import uz.usta.platform.identity.internal.domain.IdentityErrorCode;
import uz.usta.platform.identity.internal.domain.PhoneNumber;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.i18n.Messages;
import uz.usta.platform.shared.ratelimit.RateLimiter;
import uz.usta.platform.shared.security.JwtProperties;

/**
 * SMS OTP. Redis kalitlari:
 * <ul>
 *   <li>{@code otp:{phone}} — hash: {@code h} (kodning HMAC'i), {@code a} (urinishlar); TTL = {@code otp.ttl}</li>
 *   <li>{@code otp:cd:{phone}} — qayta yuborish cooldown'i</li>
 *   <li>{@code rl:otp-request:*}, {@code rl:otp-verify:*} — rate limit hisoblagichlari</li>
 * </ul>
 * Kodning o'zi saqlanmaydi: Redis sizib chiqsa ham HMAC kalitisiz kodni tiklab bo'lmaydi.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final String HASH = "h";
    private static final String ATTEMPTS = "a";

    private final StringRedisTemplate redis;
    private final OtpDelivery sms;
    private final RateLimiter rateLimiter;
    private final Messages messages;
    private final IdentityProperties.Otp cfg;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    OtpService(StringRedisTemplate redis, OtpDelivery sms, RateLimiter rateLimiter, Messages messages,
               IdentityProperties props, JwtProperties jwt,
               @Value("${app.sms.provider:log}") String smsProvider) {
        this.redis = redis;
        this.sms = sms;
        this.rateLimiter = rateLimiter;
        this.messages = messages;
        this.cfg = props.otp();
        this.hmacKey = new SecretKeySpec(jwt.secretBytes(), "HmacSHA256");
        if (cfg.fixedCode() != null) {
            if (!"log".equalsIgnoreCase(smsProvider)) {
                throw new IllegalStateException(
                        "app.identity.otp.fixed-code faqat app.sms.provider=log bilan ishlatiladi (hozir: " + smsProvider + ")");
            }
            log.warn("DIQQAT: OTP kodi doimiy ({} belgi) — faqat lokal ishlab chiqish uchun!", cfg.fixedCode().length());
        }
    }

    public record Challenge(long expiresInSeconds, long resendAfterSeconds, int codeLength) {
    }

    public Challenge request(String phone, String clientIp) {
        rateLimiter.consume("otp-request:ip", clientIp, cfg.perIpLimit(), cfg.perIpWindow());
        String cooldownKey = "otp:cd:" + phone;
        Boolean first = redis.opsForValue().setIfAbsent(cooldownKey, "1", cfg.resendCooldown());
        if (!Boolean.TRUE.equals(first)) {
            Long ttl = redis.getExpire(cooldownKey);
            long retryAfter = ttl == null || ttl < 1 ? 1 : ttl;
            throw new BusinessException(IdentityErrorCode.OTP_RESEND_TOO_EARLY, Map.of("retryAfterSeconds", retryAfter),
                    retryAfter);
        }
        rateLimiter.consume("otp-request:phone", phone, cfg.perPhoneLimit(), cfg.perPhoneWindow());

        String code = generateCode();
        String key = otpKey(phone);
        redis.delete(key);
        redis.opsForHash().putAll(key, Map.of(HASH, hash(phone, code), ATTEMPTS, "0"));
        redis.expire(key, cfg.ttl());
        try {
            sms.deliver(phone, messages.get("sms.otp", code, cfg.ttl().toMinutes()));
        } catch (OtpDelivery.DeliveryFailedException e) {
            redis.delete(key);
            redis.delete(cooldownKey);
            throw new BusinessException(IdentityErrorCode.SMS_SEND_FAILED);
        }
        log.info("OTP sent to {}", PhoneNumber.mask(phone));
        return new Challenge(cfg.ttl().toSeconds(), cfg.resendCooldown().toSeconds(), cfg.length());
    }

    /** Kodni tekshiradi; muvaffaqiyatli bo'lsa kod bir martalik sifatida o'chiriladi. */
    public void verify(String phone, String code, String clientIp) {
        rateLimiter.consume("otp-verify:ip", clientIp, cfg.verifyPerIpLimit(), cfg.perIpWindow());
        String key = otpKey(phone);
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            throw new BusinessException(IdentityErrorCode.OTP_EXPIRED);
        }
        long attempts = redis.opsForHash().increment(key, ATTEMPTS, 1);
        Object stored = redis.opsForHash().get(key, HASH);
        if (stored == null) {
            // kalit oraliqda muddati tugagan, HINCRBY esa TTL'siz bo'sh hash yaratgan bo'lishi mumkin
            redis.delete(key);
            throw new BusinessException(IdentityErrorCode.OTP_EXPIRED);
        }
        if (attempts > cfg.maxAttempts()) {
            redis.delete(key);
            throw new BusinessException(IdentityErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }
        boolean matches = MessageDigest.isEqual(stored.toString().getBytes(StandardCharsets.US_ASCII),
                hash(phone, code).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            long remaining = cfg.maxAttempts() - attempts;
            if (remaining <= 0) {
                redis.delete(key);
                throw new BusinessException(IdentityErrorCode.OTP_ATTEMPTS_EXCEEDED);
            }
            throw new BusinessException(IdentityErrorCode.OTP_INVALID, Map.of("remainingAttempts", remaining), remaining);
        }
        if (!Boolean.TRUE.equals(redis.delete(key))) {
            // parallel so'rov kodni allaqachon ishlatgan
            throw new BusinessException(IdentityErrorCode.OTP_EXPIRED);
        }
        redis.delete("otp:cd:" + phone);
    }

    Duration ttl() {
        return cfg.ttl();
    }

    private String generateCode() {
        if (cfg.fixedCode() != null) {
            return cfg.fixedCode();
        }
        int bound = (int) Math.pow(10, cfg.length());
        return String.format("%0" + cfg.length() + "d", random.nextInt(bound));
    }

    private String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String otpKey(String phone) {
        return "otp:" + phone;
    }
}
