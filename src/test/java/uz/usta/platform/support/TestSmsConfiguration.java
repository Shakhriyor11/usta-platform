package uz.usta.platform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestSmsConfiguration {

    @Bean
    @Primary
    RecordingSmsSender recordingSmsSender() {
        return new RecordingSmsSender();
    }
}
