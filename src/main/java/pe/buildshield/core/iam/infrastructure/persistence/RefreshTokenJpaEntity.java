package pe.buildshield.core.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.core.iam.domain.model.RefreshToken;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla iam.refresh_tokens. Lleva {@code organization_id}, pero sin filtro multiempresa: es una tabla
 * de seguridad que solo usa iam y se consulta por el hash del token, antes de conocer la organización.
 */
@Entity
@Table(schema = "iam", name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshTokenJpaEntity() {
    }

    static RefreshTokenJpaEntity from(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.id = token.id();
        entity.userId = token.userId();
        entity.organizationId = token.organizationId();
        entity.tokenHash = token.tokenHash();
        entity.issuedAt = token.issuedAt();
        entity.expiresAt = token.expiresAt();
        entity.revokedAt = token.revokedAt();
        entity.replacedBy = token.replacedBy();
        return entity;
    }

    RefreshToken toDomain() {
        return RefreshToken.restore(id, userId, organizationId, tokenHash, issuedAt, expiresAt, revokedAt, replacedBy);
    }
}
