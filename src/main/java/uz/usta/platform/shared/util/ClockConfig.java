package uz.usta.platform.shared.util;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Vaqtga bog'liq logikani test qilish oson bo'lishi uchun yagona {@link Clock} (UTC). */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
