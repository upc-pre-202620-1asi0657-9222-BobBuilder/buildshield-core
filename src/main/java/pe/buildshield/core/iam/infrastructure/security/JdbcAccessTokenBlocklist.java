package pe.buildshield.core.iam.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.buildshield.commons.security.RevokedTokenStore;
import pe.buildshield.core.iam.domain.model.TokenRepositories.AccessTokenBlocklist;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

/**
 * Lista de revocación de tokens de acceso (tabla iam.revoked_access_tokens). La consulta el
 * {@code JwtAuthenticationFilter} de commons en cada petición, por eso usa JDBC directo y una sola
 * consulta por clave primaria.
 */
@Component
class JdbcAccessTokenBlocklist implements RevokedTokenStore, AccessTokenBlocklist {

    private static final Logger log = LoggerFactory.getLogger(JdbcAccessTokenBlocklist.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcAccessTokenBlocklist(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        Boolean revoked = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM iam.revoked_access_tokens WHERE jti = ? AND expires_at > ?)",
                Boolean.class, tokenId, Timestamp.from(clock.instant()));
        return Boolean.TRUE.equals(revoked);
    }

    @Override
    public void block(String tokenId, Instant until) {
        jdbc.update("""
                        INSERT INTO iam.revoked_access_tokens (jti, expires_at) VALUES (?, ?)
                        ON CONFLICT (jti) DO UPDATE SET expires_at = GREATEST(iam.revoked_access_tokens.expires_at, EXCLUDED.expires_at)
                        """, tokenId, Timestamp.from(until));
    }

    /** Un token vencido ya no pasa la validación de vigencia: no hace falta recordarlo. */
    @Scheduled(fixedDelayString = "${buildshield.iam.revoked-token-purge-interval:PT1H}")
    int purgeExpired() {
        int deleted = jdbc.update("DELETE FROM iam.revoked_access_tokens WHERE expires_at <= ?",
                Timestamp.from(clock.instant()));
        if (deleted > 0) {
            log.info("Se borraron {} tokens de acceso revocados ya vencidos", deleted);
        }
        return deleted;
    }
}
