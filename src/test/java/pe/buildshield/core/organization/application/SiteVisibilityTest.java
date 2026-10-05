package pe.buildshield.core.organization.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.StaffAssignment;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.WarehouseType;
import pe.buildshield.core.organization.domain.model.WasteTolerance;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Matriz de visibilidad por rol (US17), sin base de datos. */
class SiteVisibilityTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID JORGE = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();

    private final WorksiteRepository worksites = mock(WorksiteRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
    private final SiteVisibility visibility = new SiteVisibility(worksites, warehouses, materials, assignments);

    private final Worksite torreNorte = worksite("Torre Norte");
    private final Worksite torreSur = worksite("Torre Sur");
    private final Warehouse central = warehouse("Central", true);
    private final Warehouse callao = warehouse("Callao", false);
    private final Material activeMaterial = material(true);
    private final Material retiredMaterial = material(false);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void administrator_sees_everything() {
        as(UUID.randomUUID(), "ADMINISTRATOR");
        when(worksites.findAll()).thenReturn(List.of(torreNorte, torreSur));
        when(warehouses.findAll()).thenReturn(List.of(central, callao));
        when(materials.findAll()).thenReturn(List.of(activeMaterial, retiredMaterial));

        assertThat(visibility.visibleWorksites()).containsExactly(torreNorte, torreSur);
        assertThat(visibility.visibleWarehouses()).containsExactly(central, callao);
        assertThat(visibility.visibleMaterials()).hasSize(2);
        assertThat(visibility.canSee(torreSur)).isTrue();
        assertThat(visibility.canSee(callao)).isTrue();
        assertThat(visibility.canSee(retiredMaterial)).isTrue();
    }

    @Test
    void site_manager_sees_assigned_worksites_and_active_warehouses() {
        as(JORGE, "SITE_MANAGER");
        when(assignments.activeSiteIds(JORGE, SiteType.WORKSITE)).thenReturn(Set.of(torreNorte.id()));
        when(worksites.findAllById(Set.of(torreNorte.id()))).thenReturn(List.of(torreNorte));
        when(assignments.existsActive(JORGE, torreNorte.id())).thenReturn(true);
        when(warehouses.findAllActive()).thenReturn(List.of(central));
        when(materials.findAllActive()).thenReturn(List.of(activeMaterial));

        assertThat(visibility.visibleWorksites()).containsExactly(torreNorte);
        assertThat(visibility.canSee(torreNorte)).isTrue();
        assertThat(visibility.canSee(torreSur)).isFalse();
        assertThat(visibility.visibleWarehouses()).containsExactly(central);
        assertThat(visibility.canSee(central)).isTrue();
        assertThat(visibility.canSee(callao)).isFalse();
        assertThat(visibility.visibleMaterials()).containsExactly(activeMaterial);
        assertThat(visibility.canSee(retiredMaterial)).isFalse();
    }

    @Test
    void warehouse_manager_sees_assigned_warehouses_and_no_worksites() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(assignments.activeSiteIds(ROSA, SiteType.WAREHOUSE)).thenReturn(Set.of(central.id()));
        when(warehouses.findAllById(Set.of(central.id()))).thenReturn(List.of(central));
        when(assignments.existsActive(ROSA, central.id())).thenReturn(true);

        assertThat(visibility.visibleWorksites()).isEmpty();
        assertThat(visibility.canSee(torreNorte)).isFalse();
        assertThat(visibility.visibleWarehouses()).containsExactly(central);
        assertThat(visibility.canSee(central)).isTrue();
        assertThat(visibility.canSee(callao)).isFalse();
    }

    @Test
    void unknown_roles_see_no_sites() {
        as(UUID.randomUUID(), "SYSTEM");

        assertThat(visibility.visibleWorksites()).isEmpty();
        assertThat(visibility.visibleWarehouses()).isEmpty();
        assertThat(visibility.canSee(torreNorte)).isFalse();
        assertThat(visibility.canSee(central)).isFalse();
    }

    @Test
    void managers_see_only_their_own_assignments() {
        StaffAssignment jorgesAssignment = StaffAssignment.restore(UUID.randomUUID(), JORGE, SiteType.WORKSITE,
                torreNorte.id(), Instant.now(), null, 0L);
        StaffAssignment rosasAssignment = StaffAssignment.restore(UUID.randomUUID(), ROSA, SiteType.WAREHOUSE,
                central.id(), Instant.now(), null, 0L);
        when(assignments.findByUserId(JORGE)).thenReturn(List.of(jorgesAssignment));
        when(assignments.findAll()).thenReturn(List.of(jorgesAssignment, rosasAssignment));

        as(JORGE, "SITE_MANAGER");
        assertThat(visibility.visibleAssignments()).containsExactly(jorgesAssignment);
        assertThat(visibility.canSee(jorgesAssignment)).isTrue();
        assertThat(visibility.canSee(rosasAssignment)).isFalse();

        as(UUID.randomUUID(), "ADMINISTRATOR");
        assertThat(visibility.visibleAssignments()).hasSize(2);
        assertThat(visibility.canSee(rosasAssignment)).isTrue();
    }

    private static void as(UUID userId, String role) {
        TenantContext.set(new TenantInfo(ORG, userId, role));
    }

    private static Worksite worksite(String name) {
        return Worksite.restore(UUID.randomUUID(), name, new Location("Av. 1", "Lince", "Lima", null, null),
                LocalDate.of(2026, 11, 1), null, 0L);
    }

    private static Warehouse warehouse(String name, boolean active) {
        return Warehouse.restore(UUID.randomUUID(), name, WarehouseType.WAREHOUSE, "Av. 1", active, 0L);
    }

    private static Material material(boolean active) {
        return Material.restore(UUID.randomUUID(), new Sku("CEM-" + (active ? 1 : 2)), "Cemento", UnitOfMeasure.BAG,
                new WasteTolerance(BigDecimal.ONE), active, 0L);
    }
}
