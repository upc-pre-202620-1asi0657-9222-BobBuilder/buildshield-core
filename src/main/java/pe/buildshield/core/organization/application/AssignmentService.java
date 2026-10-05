package pe.buildshield.core.organization.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.organization.StaffDirectory;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.StaffAssignment;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * US17: asignación de encargados a obras y almacenes. El rol debe corresponder al lugar, el lugar
 * debe estar activo y no se repite una asignación activa. Terminar una asignación la conserva como
 * historial.
 */
@Service
public class AssignmentService {

    public static final String ASSIGNMENT_ALREADY_ACTIVE = "ASSIGNMENT_ALREADY_ACTIVE";
    public static final String SITE_INACTIVE = "SITE_INACTIVE";

    private final AuditTrail audit;
    private final StaffAssignmentRepository assignments;
    private final WorksiteRepository worksites;
    private final WarehouseRepository warehouses;
    private final StaffDirectory staff;
    private final SiteVisibility visibility;
    private final Clock clock;

    public AssignmentService(StaffAssignmentRepository assignments, WorksiteRepository worksites,
            WarehouseRepository warehouses, StaffDirectory staff, SiteVisibility visibility, Clock clock, AuditTrail audit) {
        this.assignments = assignments;
        this.worksites = worksites;
        this.warehouses = warehouses;
        this.staff = staff;
        this.visibility = visibility;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public AssignmentView assign(AssignStaff command) {
        StaffDirectory.StaffMember member = staff.findMember(command.userId())
                .filter(StaffDirectory.StaffMember::active)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "El usuario no existe"));
        requireActiveSite(command.siteType(), command.siteId());
        StaffAssignment assignment = StaffAssignment.assign(member.userId(), member.role(), command.siteType(),
                command.siteId(), clock.instant());
        if (assignments.existsActive(command.userId(), command.siteId())) {
            throw alreadyActive();
        }
        try {
            return audit.recorded("ASSIGNMENT_CREATED", "ASSIGNMENT", AssignmentView.of(assignments.save(assignment)));
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_staff_assignments_active")) {
                throw alreadyActive();
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public AssignmentView get(UUID id) {
        return AssignmentView.of(assignments.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> list() {
        return visibility.visibleAssignments().stream().map(AssignmentView::of).toList();
    }

    /** Solo admite terminar la asignación ({@code active=false}); para retomarla se crea otra. */
    @Transactional
    public AssignmentView update(UUID id, UpdateAssignment command) {
        StaffAssignment assignment = assignments.findById(id).orElseThrow(() -> notFound(id));
        if (!Boolean.FALSE.equals(command.active())) {
            throw new ValidationException("INVALID_ASSIGNMENT_CHANGE",
                    "Solo se puede terminar una asignación (active=false); para retomarla crea una nueva",
                    List.of(new ErrorDetail("active", "debe ser false")));
        }
        assignment.end(clock.instant());
        return audit.recorded("ASSIGNMENT_ENDED", "ASSIGNMENT", AssignmentView.of(assignments.save(assignment)));
    }

    private void requireActiveSite(SiteType siteType, UUID siteId) {
        if (siteType == SiteType.WORKSITE) {
            worksites.findById(siteId).orElseThrow(() -> WorksiteService.notFound(siteId));
        } else if (siteType == SiteType.WAREHOUSE) {
            Warehouse warehouse = warehouses.findById(siteId).orElseThrow(() -> WarehouseService.notFound(siteId));
            if (!warehouse.active()) {
                throw new ConflictException(SITE_INACTIVE, "El almacén está desactivado: no se le asigna personal");
            }
        }
    }

    private static ConflictException alreadyActive() {
        return new ConflictException(ASSIGNMENT_ALREADY_ACTIVE, "El usuario ya está asignado a ese lugar");
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("ASSIGNMENT_NOT_FOUND", "La asignación " + id + " no existe");
    }

    public record AssignStaff(UUID userId, SiteType siteType, UUID siteId) {
    }

    public record UpdateAssignment(Boolean active) {
    }

    public record AssignmentView(UUID id, UUID userId, SiteType siteType, UUID siteId, Instant assignedAt,
            Instant endedAt, boolean active) {

        static AssignmentView of(StaffAssignment assignment) {
            return new AssignmentView(assignment.id(), assignment.userId(), assignment.siteType(), assignment.siteId(),
                    assignment.assignedAt(), assignment.endedAt(), assignment.isActive());
        }
    }
}
