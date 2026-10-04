package pe.buildshield.core.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.core.iam.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.UUID;

/** Tabla iam.password_reset_tokens. Sin filtro multiempresa: se busca por el hash antes de conocer la organización. */
@Entity
@Table(schema = "iam", name = "password_reset_tokens")
public class PasswordResetTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetTokenJpaEntity() {
    }

    static PasswordResetTokenJpaEntity from(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
        entity.id = token.id();
        entity.userId = token.userId();
        entity.organizationId = token.organizationId();
        entity.tokenHash = token.tokenHash();
        entity.expiresAt = token.expiresAt();
        entity.usedAt = token.usedAt();
        return entity;
    }

    PasswordResetToken toDomain() {
        return PasswordResetToken.restore(id, userId, organizationId, tokenHash, expiresAt, usedAt);
    }
}
