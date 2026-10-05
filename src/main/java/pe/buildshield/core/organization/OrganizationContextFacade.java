package pe.buildshield.core.organization;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fachada pública del módulo organization para otros módulos del Core (pedidos, despachos,
 * inventario). Devuelve copias inmutables (snapshots), nunca entidades ni repositorios.
 *
 * <p>Trabaja dentro de la organización del {@code TenantContext}: un id de otra organización
 * devuelve vacío. No aplica la visibilidad por rol: eso lo decide cada módulo con
 * {@link #isAssigned(UUID, UUID)}.
 */
@Component
public class OrganizationContextFacade {

    private final WorksiteRepository worksites;
    private final WarehouseRepository warehouses;
    private final MaterialRepository materials;
    private final StaffAssignmentRepository assignments;

    public OrganizationContextFacade(WorksiteRepository worksites, WarehouseRepository warehouses,
            MaterialRepository materials, StaffAssignmentRepository assignments) {
        this.worksites = worksites;
        this.warehouses = warehouses;
        this.materials = materials;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public Optional<WorksiteSnapshot> findWorksite(UUID worksiteId) {
        return worksites.findById(worksiteId).map(WorksiteSnapshot::of);
    }

    @Transactional(readOnly = true)
    public Optional<WarehouseSnapshot> findWarehouse(UUID warehouseId) {
        return warehouses.findById(warehouseId).map(WarehouseSnapshot::of);
    }

    @Transactional(readOnly = true)
    public Optional<MaterialSnapshot> findMaterial(UUID materialId) {
        return materials.findById(materialId).map(MaterialSnapshot::of);
    }

    /** El SKU no distingue mayúsculas. Un SKU con formato inválido no existe. */
    @Transactional(readOnly = true)
    public Optional<MaterialSnapshot> findMaterialBySku(String sku) {
        Sku parsed;
        try {
            parsed = new Sku(sku);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
        return materials.findBySku(parsed).map(MaterialSnapshot::of);
    }

    /** ¿El usuario está asignado hoy a esa obra o almacén? */
    @Transactional(readOnly = true)
    public boolean isAssigned(UUID userId, UUID siteId) {
        return assignments.existsActive(userId, siteId);
    }

    /** Obras y almacenes a los que el usuario está asignado hoy. */
    @Transactional(readOnly = true)
    public Set<UUID> assignedSites(UUID userId) {
        Set<UUID> sites = new HashSet<>(assignments.activeSiteIds(userId, SiteType.WORKSITE));
        sites.addAll(assignments.activeSiteIds(userId, SiteType.WAREHOUSE));
        return Set.copyOf(sites);
    }

    public record WorksiteSnapshot(UUID id, String name, String address, String district, String city,
            LocalDate startDate, LocalDate endDate) {

        static WorksiteSnapshot of(Worksite worksite) {
            return new WorksiteSnapshot(worksite.id(), worksite.name(), worksite.location().address(),
                    worksite.location().district(), worksite.location().city(), worksite.startDate(), worksite.endDate());
        }

        /** ¿La obra está vigente en esa fecha (dentro de su rango de fechas)? */
        public boolean isOpenOn(LocalDate date) {
            return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
        }
    }

    /** @param type WAREHOUSE o COLLECTION_CENTER */
    public record WarehouseSnapshot(UUID id, String name, String type, String address, boolean active) {

        static WarehouseSnapshot of(Warehouse warehouse) {
            return new WarehouseSnapshot(warehouse.id(), warehouse.name(), warehouse.type().name(), warehouse.address(),
                    warehouse.active());
        }
    }

    /** @param unit código de la unidad (BAG, KG, M3, …) */
    public record MaterialSnapshot(UUID id, String sku, String name, String unit, BigDecimal wasteTolerancePercent,
            boolean active) {

        static MaterialSnapshot of(Material material) {
            return new MaterialSnapshot(material.id(), material.sku().value(), material.name(), material.unit().name(),
                    material.wasteTolerance().percent(), material.active());
        }
    }
}
