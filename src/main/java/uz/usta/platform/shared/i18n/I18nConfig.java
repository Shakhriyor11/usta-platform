package uz.usta.platform.shared.i18n;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Til {@code Accept-Language} sarlavhasidan aniqlanadi. Qo'llab-quvvatlanmaydigan til kelsa —
 * standart til (o'zbekcha) ishlatiladi.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(I18nConfig.I18nProperties.class)
class I18nConfig {

    @Bean(name = "localeResolver")
    LocaleResolver localeResolver(I18nProperties props) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(props.defaultLocale());
        resolver.setSupportedLocales(props.supportedLocales());
        return resolver;
    }

    @ConfigurationProperties("app.i18n")
    record I18nProperties(Locale defaultLocale, List<Locale> supportedLocales) {
        I18nProperties {
            defaultLocale = defaultLocale == null ? Locale.of("uz") : defaultLocale;
            supportedLocales = supportedLocales == null || supportedLocales.isEmpty()
                    ? List.of(Locale.of("uz"), Locale.of("ru"), Locale.of("en"))
                    : List.copyOf(supportedLocales);
        }
    }
}
