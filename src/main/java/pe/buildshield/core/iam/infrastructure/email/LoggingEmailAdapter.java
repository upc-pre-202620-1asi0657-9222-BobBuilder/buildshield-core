package pe.buildshield.core.iam.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.buildshield.core.iam.application.EmailPort;
import pe.buildshield.core.iam.domain.model.EmailAddress;

import java.time.Instant;

/**
 * Adaptador falso de correo: escribe el mensaje en el log en lugar de enviarlo. Solo existe en los
 * perfiles local y test, porque el enlace de recuperación es una credencial. En producción la
 * aplicación no arranca hasta que haya un adaptador real de {@link EmailPort}.
 */
@Component
@Profile({"local", "test"})
class LoggingEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailAdapter.class);

    @Override
    public void sendPasswordReset(EmailAddress to, String fullName, String resetLink, Instant expiresAt) {
        log.info("""
                [CORREO FALSO] Para: {}
                Hola {}, recibimos una solicitud para restablecer tu contraseña de BuildShield.
                Usa este enlace antes de {}: {}
                Si no lo solicitaste, ignora este mensaje.""", to.value(), fullName, expiresAt, resetLink);
    }
}
