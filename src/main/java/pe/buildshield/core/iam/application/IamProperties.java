package pe.buildshield.core.iam.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración del módulo iam ({@code buildshield.iam.*}).
 *
 * @param refreshTokenTtl  duración de la sesión renovable
 * @param passwordResetTtl vigencia del enlace de recuperación de contraseña
 * @param passwordResetUrl página de la web donde el usuario define la nueva contraseña
 */
@ConfigurationProperties(prefix = "buildshield.iam")
public record IamProperties(Duration refreshTokenTtl, Duration passwordResetTtl, String passwordResetUrl) {

    public IamProperties {
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        passwordResetTtl = passwordResetTtl == null ? Duration.ofMinutes(30) : passwordResetTtl;
    }
}
