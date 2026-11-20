package uz.usta.platform.identity.internal.domain;

import java.util.regex.Pattern;
import uz.usta.platform.shared.error.BusinessException;

/**
 * O'zbekiston mobil raqamini E.164 ko'rinishiga ({@code +998XXXXXXXXX}) keltiradi.
 * Qabul qilinadi: {@code +998 90 123-45-67}, {@code 998901234567}, {@code 901234567}, {@code (90) 123 45 67}.
 */
public final class PhoneNumber {

    private static final Pattern E164_UZ = Pattern.compile("^\\+998\\d{9}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-()]");

    private PhoneNumber() {
    }

    public static String normalize(String input) {
        if (input == null) {
            throw invalid();
        }
        String s = SEPARATORS.matcher(input.trim()).replaceAll("");
        if (s.matches("\\d{9}")) {
            s = "+998" + s;
        } else if (s.matches("998\\d{9}")) {
            s = "+" + s;
        }
        if (!E164_UZ.matcher(s).matches()) {
            throw invalid();
        }
        return s;
    }

    /** Loglar uchun: {@code +998901234567 -> +99890***4567}. */
    public static String mask(String phone) {
        return phone == null || phone.length() < 9 ? "***" : phone.substring(0, 6) + "***" + phone.substring(phone.length() - 4);
    }

    private static BusinessException invalid() {
        return new BusinessException(IdentityErrorCode.INVALID_PHONE_NUMBER);
    }
}
