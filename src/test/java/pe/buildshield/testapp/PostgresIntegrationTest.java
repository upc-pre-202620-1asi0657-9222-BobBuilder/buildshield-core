package pe.buildshield.testapp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prueba de integración con la aplicación de prueba y PostgreSQL real. Cada clase debe declarar
 * además {@code @Testcontainers(disabledWithoutDocker = true)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {TestApplication.class, PostgresContainerConfig.class})
@ActiveProfiles("it")
public @interface PostgresIntegrationTest {
}
