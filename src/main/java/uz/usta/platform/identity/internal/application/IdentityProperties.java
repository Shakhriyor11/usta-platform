package uz.usta.platform.identity.internal.application;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param superAdminPhones      shu raqamlar SUPER_ADMIN bo'ladi (yagona manba — ro'yxatdan olinsa, huquq ham
 *                              olinadi). Oddiy adminlarni super admin API orqali tayinlaydi
 * @param refreshTokenTtl       refresh token amal qilish muddati
 * @param refreshReuseGrace     shu vaqt ichida eski refresh token qayta kelsa (parallel so'rov) — family
 *                              bekor qilinmaydi, faqat rad etiladi
 */
@ConfigurationProperties("app.identity")
public record IdentityProperties(Otp otp, Duration refreshTokenTtl, Duration refreshReuseGrace,
                                 Set<String> superAdminPhones) {

    public IdentityProperties {
        otp = otp == null ? new Otp(0, null, 0, null, 0, null, 0, null, 0, null, 0, 0) : otp;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        refreshReuseGrace = refreshReuseGrace == null ? Duration.ofSeconds(10) : refreshReuseGrace;
        superAdminPhones = superAdminPhones == null ? Set.of() : Set.copyOf(superAdminPhones);
    }

    /**
     * @param length          kod uzunligi
     * @param ttl             kod amal qilish muddati ({@code otp:{phone}} TTL)
     * @param maxAttempts     bitta kod uchun tekshirish urinishlari
     * @param resendCooldown  qayta yuborishgacha minimal vaqt
     * @param perPhoneLimit   bir raqamga {@code perPhoneWindow} ichida yuboriladigan SMS soni
     * @param perIpLimit      bir IP'dan {@code perIpWindow} ichida OTP so'rovlari soni
     * @param verifyPerIpLimit bir IP'dan {@code perIpWindow} ichida tekshirish so'rovlari soni
     * @param fixedCode       FAQAT lokal ishlab chiqish uchun: berilsa, tasodifiy kod o'rniga shu kod ishlatiladi.
     *                        {@code app.sms.provider=log} bo'lmasa ilova ishga tushmaydi
     * @param perPhoneDailyLimit bir raqamga 24 soatda yuboriladigan SMS soni
     * @param maxFailuresPerDay  bir raqam uchun 24 soatda (barcha kodlar bo'yicha) noto'g'ri urinishlar soni —
     *                           oshsa kod so'rash va tekshirish to'xtaydi (kodni tanlashga qarshi)
     */
    public record Otp(int length, Duration ttl, int maxAttempts, Duration resendCooldown,
                      int perPhoneLimit, Duration perPhoneWindow, int perIpLimit, Duration perIpWindow,
                      int verifyPerIpLimit, String fixedCode, int perPhoneDailyLimit, int maxFailuresPerDay) {
        public Otp {
            length = length <= 0 ? 5 : length;
            ttl = ttl == null ? Duration.ofMinutes(2) : ttl;
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            resendCooldown = resendCooldown == null ? Duration.ofSeconds(60) : resendCooldown;
            perPhoneLimit = perPhoneLimit <= 0 ? 5 : perPhoneLimit;
            perPhoneWindow = perPhoneWindow == null ? Duration.ofHours(1) : perPhoneWindow;
            perIpLimit = perIpLimit <= 0 ? 20 : perIpLimit;
            perIpWindow = perIpWindow == null ? Duration.ofHours(1) : perIpWindow;
            verifyPerIpLimit = verifyPerIpLimit <= 0 ? 60 : verifyPerIpLimit;
            perPhoneDailyLimit = perPhoneDailyLimit <= 0 ? 10 : perPhoneDailyLimit;
            maxFailuresPerDay = maxFailuresPerDay <= 0 ? 15 : maxFailuresPerDay;
            fixedCode = fixedCode == null || fixedCode.isBlank() ? null : fixedCode.strip();
            if (fixedCode != null && !fixedCode.matches("\\d{" + length + "}")) {
                throw new IllegalArgumentException("app.identity.otp.fixed-code " + length + " xonali raqam bo'lishi kerak");
            }
        }
    }
}
