package uz.usta.platform.catalog.internal.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.i18n.LocalizedText;
import uz.usta.platform.shared.web.ApiPaths;

/**
 * Kategoriya va xizmatlar uchun tayyor ikonkalar to'plami ({@code resources/catalog/icons}, Lucide, ISC
 * litsenziyasi). Admin shulardan birini kalit bo'yicha tanlaydi; mosi bo'lmasa o'z ikonkasini yuklaydi.
 */
@Component
public class IconLibrary {

    private static final String DIR = "catalog/icons/";

    public record Icon(String key, LocalizedText name) {
    }

    private record Entry(String key, LocalizedText name) {
    }

    private final Map<String, Icon> icons = new LinkedHashMap<>();
    private final Map<String, byte[]> files = new LinkedHashMap<>();

    IconLibrary(JsonMapper json) {
        for (Entry e : json.readValue(read("icons.json"), Entry[].class)) {
            icons.put(e.key(), new Icon(e.key(), e.name()));
            files.put(e.key(), read(e.key() + ".svg")); // fayli yo'q kalit bo'lsa ilova ishga tushmaydi
        }
    }

    public List<Icon> all() {
        return List.copyOf(icons.values());
    }

    public boolean contains(String key) {
        return icons.containsKey(key);
    }

    public Optional<byte[]> svg(String key) {
        return Optional.ofNullable(files.get(key)).map(byte[]::clone);
    }

    /** To'plam ikonkasining ommaviy (nisbiy) havolasi; kalit to'plamda bo'lmasa — {@code null}. */
    public String url(String key) {
        return key != null && contains(key) ? ApiPaths.PUBLIC + "/catalog/icons/" + key + ".svg" : null;
    }

    private static byte[] read(String name) {
        try (InputStream in = new ClassPathResource(DIR + name).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Icon library resource missing: " + name, e);
        }
    }
}
