package pe.buildshield.core.inventory.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.inventory.StockService.StockLevel;
import pe.buildshield.core.inventory.domain.model.StockItem;
import pe.buildshield.core.inventory.domain.model.StockRepository;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.organization.OrganizationContextFacade.MaterialSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WarehouseSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockQueriesTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();
    private static final UUID CENTRAL = UUID.randomUUID();
    private static final UUID CALLAO = UUID.randomUUID();
    private static final UUID CEMENT = UUID.randomUUID();

    private final StockOperations operations = mock(StockOperations.class);
    private final StockRepository stock = mock(StockRepository.class);
    private final OrganizationContextFacade organization = mock(OrganizationContextFacade.class);
    private final StockQueries queries = new StockQueries(operations, stock, organization);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void assigned_warehouse_manager_registers_an_entry() {
        as(ROSA, "WAREHOUSE_MANAGER");
        givenWarehouse(CENTRAL, true);
        givenMaterial(true);
        when(organization.isAssigned(ROSA, CENTRAL)).thenReturn(true);
        StockLevel level = new StockLevel(CENTRAL, CEMENT, new BigDecimal("100"), BigDecimal.ZERO);
        when(operations.add(CENTRAL, CEMENT, new BigDecimal("100"), "Guía 1")).thenReturn(level);

        assertThat(queries.registerEntry(CENTRAL, CEMENT, new BigDecimal("100"), "Guía 1")).isEqualTo(level);
    }

    @Test
    void unassigned_or_unknown_warehouse_is_404() {
        as(ROSA, "WAREHOUSE_MANAGER");
        givenWarehouse(CALLAO, true);

        assertThatThrownBy(() -> queries.registerEntry(CALLAO, CEMENT, BigDecimal.ONE, null))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "WAREHOUSE_NOT_FOUND");
        assertThatThrownBy(() -> queries.registerEntry(UUID.randomUUID(), CEMENT, BigDecimal.ONE, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(operations, never()).add(any(), any(), any(), any());
    }

    @Test
    void inactive_warehouse_or_material_is_409_and_unknown_material_404() {
        as(UUID.randomUUID(), "ADMINISTRATOR");
        givenWarehouse(CALLAO, false);
        givenWarehouse(CENTRAL, true);

        assertThatThrownBy(() -> queries.registerEntry(CALLAO, CEMENT, BigDecimal.ONE, null))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "SITE_INACTIVE");
        assertThatThrownBy(() -> queries.registerEntry(CENTRAL, CEMENT, BigDecimal.ONE, null))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "MATERIAL_NOT_FOUND");
        givenMaterial(false);
        assertThatThrownBy(() -> queries.registerEntry(CENTRAL, CEMENT, BigDecimal.ONE, null))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MATERIAL_INACTIVE");
    }

    @Test
    void each_role_sees_the_stock_of_its_warehouses() {
        givenWarehouse(CENTRAL, true);
        givenWarehouse(CALLAO, false);
        givenMaterial(true);
        when(stock.findAll()).thenReturn(List.of(item(CENTRAL, "100"), item(CALLAO, "50")));
        when(organization.isAssigned(ROSA, CENTRAL)).thenReturn(true);

        as(UUID.randomUUID(), "ADMINISTRATOR");
        assertThat(queries.list(null)).extracting(StockQueries.StockView::warehouseId).containsExactly(CENTRAL, CALLAO);
        assertThat(queries.list(CALLAO)).extracting(StockQueries.StockView::availableQty).containsExactly(new BigDecimal("50"));

        as(ROSA, "WAREHOUSE_MANAGER");
        assertThat(queries.list(null)).extracting(StockQueries.StockView::warehouseName).containsExactly("Almacén " + CENTRAL);

        as(UUID.randomUUID(), "SITE_MANAGER");
        assertThat(queries.list(null)).extracting(StockQueries.StockView::warehouseId).containsExactly(CENTRAL);

        as(UUID.randomUUID(), "SYSTEM");
        assertThat(queries.list(null)).isEmpty();
    }

    private void givenWarehouse(UUID id, boolean active) {
        when(organization.findWarehouse(id)).thenReturn(Optional.of(
                new WarehouseSnapshot(id, "Almacén " + id, "WAREHOUSE", "Av. 1", active)));
    }

    private void givenMaterial(boolean active) {
        when(organization.findMaterial(CEMENT)).thenReturn(Optional.of(
                new MaterialSnapshot(CEMENT, "CEM-001", "Cemento", "BAG", new BigDecimal("2.50"), active)));
    }

    private static StockItem item(UUID warehouse, String available) {
        return new StockItem(UUID.randomUUID(), warehouse, CEMENT, new BigDecimal(available), BigDecimal.ZERO, 0);
    }

    private static void as(UUID user, String role) {
        TenantContext.set(new TenantInfo(ORG, user, role));
    }
}
