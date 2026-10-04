package pe.buildshield.core.iam.application;

import pe.buildshield.core.iam.domain.model.EmailAddress;

import java.time.Instant;

/** Puerto hacia el proveedor de correo (proveedor externo: se conecta con un adaptador). */
public interface EmailPort {

    void sendPasswordReset(EmailAddress to, String fullName, String resetLink, Instant expiresAt);
}
