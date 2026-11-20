package uz.usta.platform.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import uz.usta.platform.UstaApplication;

/**
 * Modul chegaralari: modullar bir-birining {@code internal} paketlariga murojaat qilmasligi va
 * sikllik bog'liqlik bo'lmasligi tekshiriladi. Hujjat (PlantUML/Canvas) build/spring-modulith-docs ga yoziladi.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(UstaApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
