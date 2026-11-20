package uz.usta.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Usta chaqirish platformasi — modular monolit kirish nuqtasi.
 *
 * <p>Har bir to'g'ridan-to'g'ri sub-paket ({@code identity}, {@code order}, ...) Spring Modulith
 * moduli hisoblanadi. {@code shared} — barcha modullar foydalanadigan ochiq "shared kernel".
 */
@Modulithic(systemName = "Usta Platform", sharedModules = "shared")
@SpringBootApplication
@ConfigurationPropertiesScan
public class UstaApplication {

    public static void main(String[] args) {
        SpringApplication.run(UstaApplication.class, args);
    }
}
