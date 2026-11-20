package uz.usta.platform;

import org.springframework.boot.SpringApplication;
import uz.usta.platform.support.TestcontainersConfiguration;

/** Lokal ishga tushirish Docker konteynerlari bilan: {@code ./gradlew bootTestRun}. */
public class TestUstaApplication {

    public static void main(String[] args) {
        SpringApplication.from(UstaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
