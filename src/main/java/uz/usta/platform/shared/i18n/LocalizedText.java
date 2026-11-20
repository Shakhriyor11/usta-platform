package uz.usta.platform.shared.i18n;

import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Ko'p tilli matn (uz majburiy, ru/en ixtiyoriy). Tanlangan tilda bo'lmasa o'zbekchaga qaytadi.
 */
public record LocalizedText(String uz, String ru, String en) {

    public String resolve(Locale locale) {
        String value = switch (locale.getLanguage()) {
            case "ru" -> ru;
            case "en" -> en;
            default -> uz;
        };
        return value == null || value.isBlank() ? uz : value;
    }

    public String current() {
        return resolve(LocaleContextHolder.getLocale());
    }
}
