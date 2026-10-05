package pe.buildshield.core.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa los trabajos periódicos del Core (por ahora, la purga de claves de idempotencia).
 * Las pruebas lo apagan con {@code buildshield.scheduling.enabled=false} para invocarlos a mano.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "buildshield.scheduling", name = "enabled", matchIfMissing = true)
@EnableScheduling
public class BuildshieldSchedulingAutoConfiguration {
}
