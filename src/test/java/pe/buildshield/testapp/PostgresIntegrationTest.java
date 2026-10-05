package pe.buildshield.testapp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Base PostgreSQL obligatoria: Testcontainers o conexión temporal externa explícita. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {TestApplication.class, PostgresContainerConfig.class})
@ActiveProfiles("it")
public @interface PostgresIntegrationTest {
}
