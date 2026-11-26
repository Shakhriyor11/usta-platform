package uz.usta.platform.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Til {@code Accept-Language} sarlavhasidan aniqlanadi: {@code uz} (lotin), {@code uz-Cyrl}, {@code ru},
 * {@code en}. Qo'llab-quvvatlanmaydigan til kelsa — standart til (o'zbekcha) ishlatiladi.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(I18nConfig.I18nProperties.class)
class I18nConfig {

    @Bean(name = "localeResolver")
    LocaleResolver localeResolver(I18nProperties props) {
        return new AcceptLanguageResolver(props.defaultLocale(),
                props.supportedLocales().stream().map(Locale::forLanguageTag).toList());
    }

    /**
     * RFC 4647 "lookup": {@code uz-Cyrl-UZ → uz-Cyrl}, {@code uz-UZ → uz}. Spring'ning
     * {@code AcceptHeaderLocaleResolver} i yozuvni (script) hisobga olmay, kirill so'rovga lotin qaytarardi.
     */
    static final class AcceptLanguageResolver implements LocaleResolver {

        private final Locale defaultLocale;
        private final List<Locale> supported;

        AcceptLanguageResolver(Locale defaultLocale, List<Locale> supported) {
            this.defaultLocale = defaultLocale;
            this.supported = supported;
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            String header = request.getHeader("Accept-Language");
            if (header == null || header.isBlank()) {
                return defaultLocale;
            }
            try {
                Locale found = Locale.lookup(Locale.LanguageRange.parse(header.replace('_', '-')), supported);
                return found != null ? found : defaultLocale;
            } catch (IllegalArgumentException e) {
                return defaultLocale;
            }
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            throw new UnsupportedOperationException("Til faqat Accept-Language orqali tanlanadi");
        }
    }

    /** @param supportedLocales BCP 47 teglari ({@code uz-Cyrl}) */
    @ConfigurationProperties("app.i18n")
    record I18nProperties(Locale defaultLocale, List<String> supportedLocales) {
        I18nProperties {
            defaultLocale = defaultLocale == null ? Locale.of("uz") : defaultLocale;
            supportedLocales = supportedLocales == null || supportedLocales.isEmpty()
                    ? List.of("uz", "uz-Cyrl", "ru", "en")
                    : List.copyOf(supportedLocales);
        }
    }
}
