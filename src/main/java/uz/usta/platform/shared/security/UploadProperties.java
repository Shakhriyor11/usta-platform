package uz.usta.platform.shared.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param limit  bir foydalanuvchi {@code window} ichida yuklay oladigan fayllar soni (barcha yuklash yo'llari)
 * @param window shu limit oynasi
 */
@ConfigurationProperties("app.upload")
public record UploadProperties(int limit, Duration window) {

    public UploadProperties {
        limit = limit <= 0 ? 60 : limit;
        window = window == null ? Duration.ofHours(1) : window;
    }
}
