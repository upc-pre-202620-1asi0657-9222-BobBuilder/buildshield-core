package pe.buildshield.core.iam.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de renovación de sesión. Solo se guarda el hash del valor entregado al cliente. Cada uso lo
 * rota: el actual queda revocado y apunta al nuevo ({@code replacedBy}).
 */
public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final UUID organizationId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private UUID replacedBy;

    private RefreshToken(UUID id, UUID userId, UUID organizationId, String tokenHash, Instant issuedAt,
            Instant expiresAt, Instant revokedAt, UUID replacedBy) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
    }

    public static RefreshToken issue(UUID userId, UUID organizationId, String tokenHash, Instant now, Duration ttl) {
        return new RefreshToken(UUID.randomUUID(), userId, organizationId, tokenHash, now, now.plus(ttl), null, null);
    }

    public static RefreshToken restore(UUID id, UUID userId, UUID organizationId, String tokenHash, Instant issuedAt,
            Instant expiresAt, Instant revokedAt, UUID replacedBy) {
        return new RefreshToken(id, userId, organizationId, tokenHash, issuedAt, expiresAt, revokedAt, replacedBy);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /**
     * Emite el siguiente token y revoca este.
     *
     * @throws AuthenticationFailedException si este token ya no está vigente
     */
    public RefreshToken rotate(String nextTokenHash, Instant now, Duration ttl) {
        if (!isActive(now)) {
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        RefreshToken next = issue(userId, organizationId, nextTokenHash, now, ttl);
        revokedAt = now;
        replacedBy = next.id;
        return next;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID replacedBy() {
        return replacedBy;
    }
}
