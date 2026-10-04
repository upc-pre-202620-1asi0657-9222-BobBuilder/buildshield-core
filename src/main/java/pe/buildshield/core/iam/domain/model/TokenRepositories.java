package pe.buildshield.core.iam.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Repositorios de los tokens de sesión y recuperación. */
public final class TokenRepositories {

    private TokenRepositories() {
    }

    public interface RefreshTokenRepository {

        /** Bloquea la fila hasta el fin de la transacción: dos renovaciones simultáneas no pueden usar el mismo token. */
        Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

        void save(RefreshToken token);

        void revokeAllActive(UUID userId, Instant now);
    }

    public interface PasswordResetTokenRepository {

        Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

        void save(PasswordResetToken token);
    }

    /** Tokens de acceso revocados antes de vencer (cierre de sesión). */
    public interface AccessTokenBlocklist {

        void block(String tokenId, Instant until);
    }
}
