package pe.buildshield.core.organization.application;

import org.junit.jupiter.api.Test;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.WarehouseType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseServiceTest {

    private static final UUID ID = UUID.randomUUID();

    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final SiteVisibility visibility = mock(SiteVisibility.class);
    private final WarehouseService service = new WarehouseService(warehouses, visibility);

    @Test
    void registers_an_active_warehouse() {
        when(warehouses.save(any())).thenAnswer(invocation -> {
            Warehouse w = invocation.getArgument(0);
            return Warehouse.restore(ID, w.name(), w.type(), w.address(), w.active(), 0L);
        });

        WarehouseService.WarehouseView view = service.register(
                new WarehouseService.RegisterWarehouse("Acopio Chosica", WarehouseType.COLLECTION_CENTER, "km 34"));

        assertThat(view).isEqualTo(new WarehouseService.WarehouseView(ID, "Acopio Chosica",
                WarehouseType.COLLECTION_CENTER, "km 34", true));
    }

    @Test
    void deactivates_reactivates_and_renames() {
        Warehouse warehouse = existing();
        when(warehouses.findById(ID)).thenReturn(Optional.of(warehouse));
        when(warehouses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(ID, new WarehouseService.UpdateWarehouse(null, null, null, false)).active()).isFalse();
        assertThat(service.update(ID, new WarehouseService.UpdateWarehouse(null, null, null, true)).active()).isTrue();
        assertThat(service.update(ID, new WarehouseService.UpdateWarehouse("Central 2", null, null, null)))
                .satisfies(view -> {
                    assertThat(view.name()).isEqualTo("Central 2");
                    assertThat(view.active()).isTrue();
                });
    }

    @Test
    void unknown_or_invisible_warehouse_is_404() {
        Warehouse warehouse = existing();
        when(warehouses.findById(ID)).thenReturn(Optional.of(warehouse));
        when(visibility.canSee(warehouse)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "WAREHOUSE_NOT_FOUND");
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new WarehouseService.UpdateWarehouse("x", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        when(visibility.canSee(warehouse)).thenReturn(true);
        assertThat(service.get(ID).name()).isEqualTo("Central");
    }

    @Test
    void list_returns_what_the_requester_can_see() {
        when(visibility.visibleWarehouses()).thenReturn(List.of(existing()));

        assertThat(service.list()).extracting(WarehouseService.WarehouseView::name).containsExactly("Central");
    }

    private static Warehouse existing() {
        return Warehouse.restore(ID, "Central", WarehouseType.WAREHOUSE, "Av. 1", true, 0L);
    }
}
