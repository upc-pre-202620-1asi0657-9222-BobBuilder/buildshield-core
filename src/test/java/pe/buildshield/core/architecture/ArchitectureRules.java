package pe.buildshield.core.architecture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura del monolito modular, parametrizadas por paquete raíz
 * para poder probarlas también contra clases de ejemplo.
 */
final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /**
     * Las capas internas de cada módulo (interfaces, application, domain, infrastructure)
     * solo pueden ser usadas desde el mismo módulo. Otros módulos únicamente ven la
     * fachada: las clases del paquete raíz del módulo.
     */
    static ArchRule moduleInternalsOnlyAccessedFromSameModule(String rootPackage, List<String> modules) {
        CompositeArchRule rule = null;
        for (String module : modules) {
            String modulePackage = rootPackage + "." + module;
            ArchRule moduleRule = classes()
                    .that().resideInAPackage(modulePackage + ".(*)..")
                    .should().onlyBeAccessed().byAnyPackage(modulePackage + "..")
                    .allowEmptyShould(true)
                    .because("el módulo '" + module + "' solo expone su fachada (paquete " + modulePackage + ")");
            rule = rule == null ? CompositeArchRule.of(moduleRule) : rule.and(moduleRule);
        }
        return rule;
    }

    /** El dominio es Java puro: no depende de Spring, JPA ni Hibernate. */
    static ArchRule domainIsFrameworkFree(String rootPackage) {
        return noClasses()
                .that().resideInAPackage(rootPackage + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..")
                .allowEmptyShould(true)
                .because("el dominio no depende de Spring ni de JPA");
    }
}
