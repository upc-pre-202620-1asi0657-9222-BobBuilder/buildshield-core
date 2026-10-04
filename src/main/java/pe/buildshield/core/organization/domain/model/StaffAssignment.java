package pe.buildshield.core.organization.domain.model;

import pe.buildshield.commons.error.ConflictException;
import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Asignación de un encargado a una obra o almacén. Determina qué ve cada usuario. Al terminar se
 * conserva como historial ({@code endedAt}).
 */
public class StaffAssignment {

    public static final String ROLE_NOT_ALLOWED_FOR_SITE = "ROLE_NOT_ALLOWED_FOR_SITE";

    private final UUID id;
    private final UUID userId;
    private final SiteType siteType;
    private final UUID siteId;
    private final Instant assignedAt;
    private Instant endedAt;
    private final Long version;

    private StaffAssignment(UUID id, UUID userId, SiteType siteType, UUID siteId, Instant assignedAt, Instant endedAt,
            Long version) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.siteType = Objects.requireNonNull(siteType, "siteType");
        this.siteId = Objects.requireNonNull(siteId, "siteId");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
        this.endedAt = endedAt;
        this.version = version;
    }

    /** @param userRole rol del usuario en iam (nombre del rol) */
    public static StaffAssignment assign(UUID userId, String userRole, SiteType siteType, UUID siteId, Instant now) {
        if (siteType == null) {
            throw new ValidationException("INVALID_SITE_TYPE", "El tipo de lugar es obligatorio",
                    List.of(new ErrorDetail("siteType", "WORKSITE o WAREHOUSE")));
        }
        if (!siteType.assignableRole().equals(userRole)) {
            throw new ValidationException(ROLE_NOT_ALLOWED_FOR_SITE,
                    "A un(a) " + siteType.displayName() + " solo se asigna el rol " + siteType.assignableRole(),
                    List.of(new ErrorDetail("userId", "rol " + userRole + " no asignable a " + siteType.displayName())));
        }
        return new StaffAssignment(null, userId, siteType, siteId, now, null, null);
    }

    public static StaffAssignment restore(UUID id, UUID userId, SiteType siteType, UUID siteId, Instant assignedAt,
            Instant endedAt, Long version) {
        return new StaffAssignment(Objects.requireNonNull(id, "id"), userId, siteType, siteId, assignedAt, endedAt, version);
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public void end(Instant now) {
        if (!isActive()) {
            throw new ConflictException("ASSIGNMENT_ALREADY_ENDED", "La asignación ya terminó");
        }
        endedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public SiteType siteType() {
        return siteType;
    }

    public UUID siteId() {
        return siteId;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Long version() {
        return version;
    }
}
