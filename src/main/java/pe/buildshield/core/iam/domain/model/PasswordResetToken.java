package pe.buildshield.core.iam.domain.model;

import pe.buildshield.core.shared.error.ValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Enlace de recuperación de contraseña: vence y se usa una sola vez. Solo se guarda su hash. */
public class PasswordResetToken {

    public static final String INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN";

    private final UUID id;
    private final UUID userId;
    private final UUID organizationId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant usedAt;

    private PasswordResetToken(UUID id, UUID userId, UUID organizationId, String tokenHash, Instant expiresAt,
            Instant usedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.usedAt = usedAt;
    }

    public static PasswordResetToken issue(UUID userId, UUID organizationId, String tokenHash, Instant now, Duration ttl) {
        return new PasswordResetToken(UUID.randomUUID(), userId, organizationId, tokenHash, now.plus(ttl), null);
    }

    public static PasswordResetToken restore(UUID id, UUID userId, UUID organizationId, String tokenHash,
            Instant expiresAt, Instant usedAt) {
        return new PasswordResetToken(id, userId, organizationId, tokenHash, expiresAt, usedAt);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** Marca el enlace como usado; falla si venció o ya se usó. */
    public void use(Instant now) {
        if (!isUsable(now)) {
            throw invalid();
        }
        usedAt = now;
    }

    public static ValidationException invalid() {
        return new ValidationException(INVALID_RESET_TOKEN, "El enlace de recuperación es inválido, venció o ya se usó");
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

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }
}
