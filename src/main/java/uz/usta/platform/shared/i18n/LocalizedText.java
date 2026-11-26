package uz.usta.platform.shared.i18n;

import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Ko'p tilli matn: o'zbekcha (lotin), rus, ingliz va o'zbek kirill. Tanlangan tilda bo'lmasa o'zbekchaga qaytadi;
 * kirillcha qiymat kiritilmagan bo'lsa lotinchadan o'giriladi ({@link UzbekCyrillic}).
 */
public record LocalizedText(String uz, String ru, String en, String uzCyrl) {

    public LocalizedText(String uz, String ru, String en) {
        this(uz, ru, en, null);
    }

    public String resolve(Locale locale) {
        String value = switch (locale.getLanguage()) {
            case "ru" -> ru;
            case "en" -> en;
            default -> "Cyrl".equals(locale.getScript()) ? cyrillic() : uz;
        };
        return value == null || value.isBlank() ? uz : value;
    }

    public String current() {
        return resolve(LocaleContextHolder.getLocale());
    }

    /** Kirillcha qiymat: kiritilgani yoki lotinchadan o'girilgani. */
    public String cyrillic() {
        return uzCyrl == null || uzCyrl.isBlank() ? UzbekCyrillic.transliterate(uz) : uzCyrl;
    }
}
