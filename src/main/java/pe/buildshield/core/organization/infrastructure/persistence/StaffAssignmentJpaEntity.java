package pe.buildshield.core.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.buildshield.commons.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.StaffAssignment;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla organization.staff_assignments. El lugar va en {@code worksite_id} o {@code warehouse_id}
 * según el tipo, para tener llave foránea a cada tabla dentro del esquema.
 */
@Entity
@Table(schema = "organization", name = "staff_assignments")
public class StaffAssignmentJpaEntity extends AuditableAbstractAggregateRoot<StaffAssignmentJpaEntity> {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_type", nullable = false, updatable = false, length = 20)
    private SiteType siteType;

    @Column(name = "worksite_id", updatable = false)
    private UUID worksiteId;

    @Column(name = "warehouse_id", updatable = false)
    private UUID warehouseId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected StaffAssignmentJpaEntity() {
    }

    StaffAssignmentJpaEntity(StaffAssignment assignment) {
        userId = assignment.userId();
        siteType = assignment.siteType();
        if (siteType == SiteType.WORKSITE) {
            worksiteId = assignment.siteId();
        } else {
            warehouseId = assignment.siteId();
        }
        assignedAt = assignment.assignedAt();
        apply(assignment);
    }

    void apply(StaffAssignment assignment) {
        endedAt = assignment.endedAt();
    }

    UUID siteId() {
        return siteType == SiteType.WORKSITE ? worksiteId : warehouseId;
    }

    SiteType siteType() {
        return siteType;
    }

    StaffAssignment toDomain() {
        return StaffAssignment.restore(getId(), userId, siteType, siteId(), assignedAt, endedAt, getVersion());
    }
}
