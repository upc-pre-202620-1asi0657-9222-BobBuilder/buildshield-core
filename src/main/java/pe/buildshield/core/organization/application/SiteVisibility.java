package pe.buildshield.core.organization.application;

import org.springframework.stereotype.Component;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.StaffAssignment;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.util.List;

/**
 * Qué puede ver quien hace la petición dentro de su organización (US17), según el rol del token:
 *
 * <ul>
 *   <li>Administrador: todo.</li>
 *   <li>Encargado de obra: sus obras asignadas y los almacenes activos (para pedirles material).</li>
 *   <li>Encargado de almacén: sus almacenes asignados; ninguna obra.</li>
 *   <li>Catálogo: todos; los encargados, solo materiales activos.</li>
 *   <li>Asignaciones: los encargados, solo las propias.</li>
 * </ul>
 *
 * Lo que no puede ver se trata como inexistente (404), igual que lo de otra organización.
 */
@Component
public class SiteVisibility {

    static final String ADMINISTRATOR = "ADMINISTRATOR";
    static final String SITE_MANAGER = "SITE_MANAGER";
    static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";

    private final WorksiteRepository worksites;
    private final WarehouseRepository warehouses;
    private final MaterialRepository materials;
    private final StaffAssignmentRepository assignments;

    public SiteVisibility(WorksiteRepository worksites, WarehouseRepository warehouses, MaterialRepository materials,
            StaffAssignmentRepository assignments) {
        this.worksites = worksites;
        this.warehouses = warehouses;
        this.materials = materials;
        this.assignments = assignments;
    }

    boolean isAdministrator() {
        return ADMINISTRATOR.equals(requester().role());
    }

    List<Worksite> visibleWorksites() {
        return switch (requester().role()) {
            case ADMINISTRATOR -> worksites.findAll();
            case SITE_MANAGER -> worksites.findAllById(assignedSites(SiteType.WORKSITE));
            default -> List.of();
        };
    }

    boolean canSee(Worksite worksite) {
        return switch (requester().role()) {
            case ADMINISTRATOR -> true;
            case SITE_MANAGER -> assignments.existsActive(requester().userId(), worksite.id());
            default -> false;
        };
    }

    List<Warehouse> visibleWarehouses() {
        return switch (requester().role()) {
            case ADMINISTRATOR -> warehouses.findAll();
            case WAREHOUSE_MANAGER -> warehouses.findAllById(assignedSites(SiteType.WAREHOUSE));
            case SITE_MANAGER -> warehouses.findAllActive();
            default -> List.of();
        };
    }

    boolean canSee(Warehouse warehouse) {
        return switch (requester().role()) {
            case ADMINISTRATOR -> true;
            case WAREHOUSE_MANAGER -> assignments.existsActive(requester().userId(), warehouse.id());
            case SITE_MANAGER -> warehouse.active();
            default -> false;
        };
    }

    List<Material> visibleMaterials() {
        return isAdministrator() ? materials.findAll() : materials.findAllActive();
    }

    boolean canSee(Material material) {
        return material.active() || isAdministrator();
    }

    List<StaffAssignment> visibleAssignments() {
        return isAdministrator() ? assignments.findAll() : assignments.findByUserId(requester().userId());
    }

    boolean canSee(StaffAssignment assignment) {
        return isAdministrator() || assignment.userId().equals(requester().userId());
    }

    private java.util.Set<java.util.UUID> assignedSites(SiteType type) {
        return assignments.activeSiteIds(requester().userId(), type);
    }

    private static TenantInfo requester() {
        return TenantContext.require();
    }
}
