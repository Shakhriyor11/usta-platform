package uz.usta.platform.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import uz.usta.platform.shared.error.ErrorCode;
import uz.usta.platform.shared.security.AdminPermission;

/** To'rtala til fayli bir xil kalit va o'rinbosarlarga ega, koddagi barcha xato kodlarining matni bor. */
class MessageBundlesTest {

    private static final List<String> SUFFIXES = List.of("", "_uz_Cyrl", "_ru", "_en");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9]+}");

    @Test
    void allLanguagesHaveTheSameKeysAndPlaceholders() throws IOException {
        Properties base = load("");
        for (String suffix : SUFFIXES) {
            Properties other = load(suffix);
            assertThat(new TreeSet<>(other.stringPropertyNames())).as("messages%s", suffix)
                    .isEqualTo(new TreeSet<>(base.stringPropertyNames()));
            for (String key : base.stringPropertyNames()) {
                String value = other.getProperty(key);
                assertThat(value).as("%s%s", key, suffix).isNotBlank();
                assertThat(placeholders(value)).as("%s%s", key, suffix)
                        .isEqualTo(placeholders(base.getProperty(key)));
                if (value.contains("{0}")) {
                    // oddiy ' belgisi MessageFormat'da o'rinbosarlarni "yutib yuboradi"
                    assertThat(MessageFormat.format(value, "A", "B", "C")).as("%s%s", key, suffix).contains("A");
                }
            }
        }
    }

    @Test
    void cyrillicFileIsActuallyCyrillic() throws IOException {
        assertThat(load("_uz_Cyrl").getProperty("error.access-denied")).matches("[\\p{IsCyrillic}\\p{P}\\s]+");
    }

    @Test
    void everyErrorCodeAndPermissionHasAText() throws IOException {
        Properties base = load("");
        var errorEnums = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("uz.usta.platform").stream()
                .filter(c -> c.isEnum() && c.isAssignableTo(ErrorCode.class))
                .map(JavaClass::reflect).toList();
        assertThat(errorEnums).hasSizeGreaterThan(8);
        for (Class<?> type : errorEnums) {
            Arrays.stream(type.getEnumConstants()).map(ErrorCode.class::cast).forEach(code ->
                    assertThat(base.stringPropertyNames()).as(code.code()).contains(code.messageKey()));
        }
        for (AdminPermission p : AdminPermission.values()) {
            assertThat(base.stringPropertyNames()).contains("permission." + p.name().toLowerCase());
        }
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new TreeSet<>();
        PLACEHOLDER.matcher(value).results().forEach(r -> found.add(r.group()));
        return found;
    }

    private static Properties load(String suffix) throws IOException {
        Properties p = new Properties();
        try (Reader in = new InputStreamReader(
                MessageBundlesTest.class.getResourceAsStream("/i18n/messages" + suffix + ".properties"),
                StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }
}
