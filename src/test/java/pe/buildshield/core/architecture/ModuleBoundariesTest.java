package pe.buildshield.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = ModuleBoundariesTest.CORE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    static final String CORE_PACKAGE = "pe.buildshield.core";

    static final List<String> MODULES = List.of(
            "iam", "organization", "inventory", "ordering",
            "dispatch", "reception", "subscription", "notification", "audit");

    @ArchTest
    static final ArchRule modules_only_expose_their_facade =
            ArchitectureRules.moduleInternalsOnlyAccessedFromSameModule(CORE_PACKAGE, MODULES);

    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring_or_jpa =
            ArchitectureRules.domainIsFrameworkFree(CORE_PACKAGE);

    /** El kernel compartido lo usan todos los módulos, así que no puede depender de ninguno. */
    @ArchTest
    static final ArchRule shared_kernel_does_not_depend_on_modules = noClasses()
            .that().resideInAPackage(CORE_PACKAGE + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(MODULES.stream()
                    .map(module -> CORE_PACKAGE + "." + module + "..").toArray(String[]::new));

    /**
     * Comprueba que las reglas detectan violaciones reales usando clases de ejemplo,
     * para no confiar en reglas que pasan solo porque todavía no hay código.
     */
    @Nested
    class RulesDetectViolations {

        private static final String FIXTURES = "pe.buildshield.core.architecture.fixtures";

        private final JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES);

        @Test
        void detects_a_module_using_another_modules_repository() {
            EvaluationResult result = ArchitectureRules
                    .moduleInternalsOnlyAccessedFromSameModule(FIXTURES, List.of("alpha", "beta"))
                    .evaluate(fixtures);

            assertThat(result.hasViolation()).isTrue();
            assertThat(result.getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("BetaService") && line.contains("AlphaRepository"))
                    .noneMatch(line -> line.contains("AlphaFacade"));
        }

        @Test
        void detects_spring_inside_the_domain() {
            EvaluationResult result = ArchitectureRules.domainIsFrameworkFree(FIXTURES).evaluate(fixtures);

            assertThat(result.hasViolation()).isTrue();
            assertThat(result.getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("SpringAwareEntity"));
        }
    }
}
