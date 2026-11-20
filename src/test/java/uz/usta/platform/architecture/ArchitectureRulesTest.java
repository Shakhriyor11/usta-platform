package uz.usta.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/** Kod sifati va qatlam qoidalari (modul chegaralari {@link ModularityTest} da). */
@AnalyzeClasses(packages = "uz.usta.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule noFieldInjection = fields().should().notBeAnnotatedWith(Autowired.class)
            .because("faqat konstruktor orqali inject qilinadi");

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noSystemOut = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule controllersLiveInWebPackages = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("..internal.web..", "..shared.web..");

    @ArchTest
    static final ArchRule entitiesAreNotExposedByControllers = methods().that().areDeclaredInClassesThat()
            .areAnnotatedWith(RestController.class)
            .should().notHaveRawReturnType(com.tngtech.archunit.base.DescribedPredicate.describe(
                    "JPA entity", c -> c.isAnnotatedWith(Entity.class)))
            .allowEmptyShould(true)
            .because("API faqat DTO qaytaradi");

    @ArchTest
    static final ArchRule entitiesLiveInInternalDomain = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..internal.domain..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule webDoesNotTouchRepositoriesDirectly = noClasses().that().resideInAPackage("..internal.web..")
            .should().dependOnClassesThat().resideInAnyPackage("..internal.persistence..", "..internal.repo..")
            .allowEmptyShould(true)
            .because("controller -> service -> repository");
}
