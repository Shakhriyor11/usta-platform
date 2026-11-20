package uz.usta.platform.search.internal.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param defaultRadiusKm qidiruv radiusi berilmasa
 * @param maxRadiusKm     ruxsat etilgan eng katta radius
 * @param cacheTtl        qidiruv natijalari Redis keshida turish muddati
 * @param onlineTtl       usta heartbeat yubormasa shu muddatdan keyin offline hisoblanadi
 */
@ConfigurationProperties("app.search")
public record SearchProperties(int defaultRadiusKm, int maxRadiusKm, Duration cacheTtl, Duration onlineTtl) {

    public SearchProperties {
        defaultRadiusKm = defaultRadiusKm <= 0 ? 20 : defaultRadiusKm;
        maxRadiusKm = maxRadiusKm <= 0 ? 100 : maxRadiusKm;
        cacheTtl = cacheTtl == null ? Duration.ofSeconds(60) : cacheTtl;
        onlineTtl = onlineTtl == null ? Duration.ofSeconds(90) : onlineTtl;
    }
}
