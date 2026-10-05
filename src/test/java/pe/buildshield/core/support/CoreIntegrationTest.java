package pe.buildshield.core.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Base PostgreSQL obligatoria: Testcontainers o conexión temporal externa explícita. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(ContainersConfig.class)
public @interface CoreIntegrationTest {
}
