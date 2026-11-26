package uz.usta.platform.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class UzbekCyrillicTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Santexnika            | Сантехника
            Quvur va oqish        | Қувур ва оқиш
            Jo‘mrak o‘rnatish     | Жўмрак ўрнатиш
            Gʻisht terish         | Ғишт териш
            Yo'l yotqizish        | Йўл ётқизиш
            Elektr ishlari        | Электр ишлари
            Poezd                 | Поэзд
            Yetkazib berish       | Етказиб бериш
            Ma'lumot              | Маълумот
            Is'hoq                | Исҳоқ
            Tsex                  | Цех
            Stansiya              | Стансия
            Informatsiya          | Информация
            Ketsa                 | Кетса
            Choyshab yuvish       | Чойшаб ювиш
            SMS yuborish {0} UZS  | SMS юбориш {0} UZS
            Wi-Fi sozlash         | Wi-Фи созлаш
            pushToken kiriting    | pushToken киритинг
            """)
    void transliterates(String latin, String cyrillic) {
        assertThat(UzbekCyrillic.transliterate(latin)).isEqualTo(cyrillic);
    }

    @Test
    void localizedTextPrefersTheEnteredCyrillicValue() {
        Locale cyrl = Locale.forLanguageTag("uz-Cyrl");
        assertThat(new LocalizedText("Santexnika", "Сантехника", "Plumbing", null).resolve(cyrl))
                .isEqualTo("Сантехника");
        assertThat(new LocalizedText("Konditsioner", null, null, "Кондиционер").resolve(cyrl))
                .isEqualTo("Кондиционер");
        assertThat(new LocalizedText("Bo'yash", null, "Painting").resolve(Locale.forLanguageTag("uz")))
                .isEqualTo("Bo'yash");
        assertThat(new LocalizedText("Bo'yash", null, null).resolve(Locale.forLanguageTag("ru"))).isEqualTo("Bo'yash");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            uz-Cyrl                 | uz-Cyrl
            uz-Cyrl-UZ              | uz-Cyrl
            uz_Cyrl                 | uz-Cyrl
            uz-UZ                   | uz
            uz-Latn-UZ              | uz
            ru-RU,ru;q=0.9          | ru
            fr-FR, en;q=0.5         | en
            ru;q=0.2, uz-Cyrl;q=0.8 | uz-Cyrl
            de                      | uz
            ;;;                     | uz
            """)
    void resolvesAcceptLanguage(String header, String expected) {
        var resolver = new I18nConfig.AcceptLanguageResolver(Locale.of("uz"),
                java.util.stream.Stream.of("uz", "uz-Cyrl", "ru", "en").map(Locale::forLanguageTag).toList());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", header);
        assertThat(resolver.resolveLocale(request).toLanguageTag()).isEqualTo(expected);
    }
}
