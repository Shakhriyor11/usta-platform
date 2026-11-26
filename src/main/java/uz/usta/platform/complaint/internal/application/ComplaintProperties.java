package uz.usta.platform.complaint.internal.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param createLimit  bir foydalanuvchi {@code createWindow} ichida yoza oladigan shikoyatlar soni
 * @param createWindow shu limit oynasi
 */
@ConfigurationProperties("app.complaint")
public record ComplaintProperties(int createLimit, Duration createWindow) {

    public ComplaintProperties {
        createLimit = createLimit <= 0 ? 10 : createLimit;
        createWindow = createWindow == null ? Duration.ofDays(1) : createWindow;
    }
}
