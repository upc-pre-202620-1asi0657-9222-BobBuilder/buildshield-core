package pe.buildshield.core.organization.application;

import org.springframework.stereotype.Component;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.util.List;

/**
 * Qué puede ver quien hace la petición dentro de su organización, según su rol. Lo que no puede ver
 * se trata como inexistente (404), igual que lo de otra organización.
 */
@Component
public class SiteVisibility {

    static final String ADMINISTRATOR = "ADMINISTRATOR";

    private final WorksiteRepository worksites;
    private final WarehouseRepository warehouses;
    private final MaterialRepository materials;

    public SiteVisibility(WorksiteRepository worksites, WarehouseRepository warehouses, MaterialRepository materials) {
        this.worksites = worksites;
        this.warehouses = warehouses;
        this.materials = materials;
    }

    boolean isAdministrator() {
        return ADMINISTRATOR.equals(TenantContext.require().role());
    }

    List<Worksite> visibleWorksites() {
        return worksites.findAll();
    }

    boolean canSee(Worksite worksite) {
        return true;
    }

    List<Warehouse> visibleWarehouses() {
        return warehouses.findAll();
    }

    boolean canSee(Warehouse warehouse) {
        return true;
    }

    /** Todos ven el catálogo; los encargados, solo los materiales activos. */
    List<Material> visibleMaterials() {
        return isAdministrator() ? materials.findAll() : materials.findAllActive();
    }

    boolean canSee(Material material) {
        return material.active() || isAdministrator();
    }
}
