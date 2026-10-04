package pe.buildshield.core.organization;

import org.junit.jupiter.api.Test;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.WarehouseType;
import pe.buildshield.core.organization.domain.model.WasteTolerance;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationContextFacadeTest {

    private static final UUID ID = UUID.randomUUID();

    private final WorksiteRepository worksites = mock(WorksiteRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
    private final OrganizationContextFacade facade = new OrganizationContextFacade(worksites, warehouses, materials, assignments);

    @Test
    void returns_worksite_snapshots_and_knows_if_they_are_open() {
        when(worksites.findById(ID)).thenReturn(Optional.of(Worksite.restore(ID, "Torre Norte",
                new Location("Av. 1", "Lince", "Lima", null, null), LocalDate.of(2026, 11, 1), LocalDate.of(2027, 6, 30), 0L)));

        OrganizationContextFacade.WorksiteSnapshot snapshot = facade.findWorksite(ID).orElseThrow();

        assertThat(snapshot.name()).isEqualTo("Torre Norte");
        assertThat(snapshot.city()).isEqualTo("Lima");
        assertThat(snapshot.isOpenOn(LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(snapshot.isOpenOn(LocalDate.of(2027, 6, 30))).isTrue();
        assertThat(snapshot.isOpenOn(LocalDate.of(2026, 10, 31))).isFalse();
        assertThat(snapshot.isOpenOn(LocalDate.of(2027, 7, 1))).isFalse();
        assertThat(facade.findWorksite(UUID.randomUUID())).isEmpty();
    }

    @Test
    void open_ended_worksite_is_open_after_its_start() {
        when(worksites.findById(ID)).thenReturn(Optional.of(Worksite.restore(ID, "Puente",
                new Location("Av. 1", "Rímac", "Lima", null, null), LocalDate.of(2026, 11, 1), null, 0L)));

        assertThat(facade.findWorksite(ID).orElseThrow().isOpenOn(LocalDate.of(2030, 1, 1))).isTrue();
    }

    @Test
    void returns_warehouse_and_material_snapshots() {
        when(warehouses.findById(ID)).thenReturn(Optional.of(
                Warehouse.restore(ID, "Central", WarehouseType.COLLECTION_CENTER, "Av. 1", false, 0L)));
        Material cement = Material.restore(ID, new Sku("CEM-001"), "Cemento", UnitOfMeasure.BAG,
                new WasteTolerance(new BigDecimal("2.5")), true, 0L);
        when(materials.findById(ID)).thenReturn(Optional.of(cement));
        when(materials.findBySku(new Sku("CEM-001"))).thenReturn(Optional.of(cement));

        assertThat(facade.findWarehouse(ID).orElseThrow())
                .isEqualTo(new OrganizationContextFacade.WarehouseSnapshot(ID, "Central", "COLLECTION_CENTER", "Av. 1", false));
        assertThat(facade.findMaterial(ID).orElseThrow())
                .isEqualTo(new OrganizationContextFacade.MaterialSnapshot(ID, "CEM-001", "Cemento", "BAG",
                        new BigDecimal("2.50"), true));
        assertThat(facade.findMaterialBySku("cem-001")).isPresent();
    }

    @Test
    void invalid_sku_does_not_exist() {
        assertThat(facade.findMaterialBySku("no es sku")).isEmpty();
        assertThat(facade.findMaterialBySku(null)).isEmpty();
        verify(materials, never()).findBySku(any());
    }

    @Test
    void tells_if_a_user_is_assigned_to_a_site() {
        UUID user = UUID.randomUUID();
        when(assignments.existsActive(user, ID)).thenReturn(true);

        assertThat(facade.isAssigned(user, ID)).isTrue();
        assertThat(facade.isAssigned(user, UUID.randomUUID())).isFalse();
    }
}
