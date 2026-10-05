package pe.buildshield.core.ordering.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.ordering.domain.model.InvalidOrderTransitionException;
import pe.buildshield.core.ordering.domain.model.Order;
import pe.buildshield.core.ordering.domain.model.OrderLine;
import pe.buildshield.core.ordering.domain.model.OrderRepository;
import pe.buildshield.core.ordering.domain.model.OrderStatus;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.organization.OrganizationContextFacade.MaterialSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WarehouseSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WorksiteSnapshot;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final pe.buildshield.core.audit.AuditTrail audit = pe.buildshield.core.support.AuditTestSupport.noop();
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID JORGE = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();
    private static final UUID TORRE = UUID.randomUUID();
    private static final UUID CENTRAL = UUID.randomUUID();
    private static final UUID CEMENT = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrganizationContextFacade organization = mock(OrganizationContextFacade.class);
    private final OrderService service = new OrderService(orders, organization, Clock.fixed(NOW, ZoneOffset.UTC), audit);

    @BeforeEach
    void site() {
        when(organization.findWorksite(TORRE)).thenReturn(Optional.of(new WorksiteSnapshot(TORRE, "Torre", "Av. 1",
                "Lince", "Lima", LocalDate.of(2026, 11, 1), null)));
        when(organization.isAssigned(JORGE, TORRE)).thenReturn(true);
        when(organization.isAssigned(ROSA, CENTRAL)).thenReturn(true);
        givenWarehouse(true);
        givenMaterial(true);
        when(orders.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void site_manager_places_an_order_for_an_assigned_worksite() {
        as(JORGE, "SITE_MANAGER");

        OrderService.OrderView view = service.place(placeOrder(TORRE, "50"));

        assertThat(view.id()).isEqualTo(ORDER);
        assertThat(view.status()).isEqualTo(OrderStatus.REGISTERED);
        assertThat(view.requestedBy()).isEqualTo(JORGE);
        assertThat(view.lines()).singleElement().satisfies(line -> {
            assertThat(line.sku()).isEqualTo("CEM-001");
            assertThat(line.unit()).isEqualTo("BAG");
            assertThat(line.pending()).isEqualByComparingTo("50");
        });
    }

    @Test
    void unassigned_or_unknown_worksite_is_404() {
        as(UUID.randomUUID(), "SITE_MANAGER");

        assertThatThrownBy(() -> service.place(placeOrder(TORRE, "50")))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "WORKSITE_NOT_FOUND");
        as(JORGE, "SITE_MANAGER");
        assertThatThrownBy(() -> service.place(placeOrder(UUID.randomUUID(), "50")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void warehouse_and_materials_must_exist_and_be_active() {
        as(JORGE, "SITE_MANAGER");
        givenWarehouse(false);
        assertThatThrownBy(() -> service.place(placeOrder(TORRE, "50")))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "SITE_INACTIVE");

        givenWarehouse(true);
        givenMaterial(false);
        assertThatThrownBy(() -> service.place(placeOrder(TORRE, "50")))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "MATERIAL_INACTIVE");

        when(organization.findMaterial(CEMENT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.place(placeOrder(TORRE, "50")))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "MATERIAL_NOT_FOUND");

        when(organization.findWarehouse(CENTRAL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.place(placeOrder(TORRE, "50")))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "WAREHOUSE_NOT_FOUND");
    }

    @Test
    void invalid_quantity_is_400() {
        as(JORGE, "SITE_MANAGER");

        assertThatThrownBy(() -> service.place(placeOrder(TORRE, "0"))).isInstanceOf(ValidationException.class);
    }

    @Test
    void warehouse_manager_of_the_source_warehouse_approves() {
        givenStoredOrder(OrderStatus.REGISTERED);
        as(ROSA, "WAREHOUSE_MANAGER");

        OrderService.OrderView view = service.approve(ORDER);

        assertThat(view.status()).isEqualTo(OrderStatus.IN_REVIEW);
        assertThat(view.decidedBy()).isEqualTo(ROSA);
        assertThat(view.decidedAt()).isEqualTo(NOW);
    }

    @Test
    void administrator_rejects_with_a_reason() {
        givenStoredOrder(OrderStatus.REGISTERED);
        as(UUID.randomUUID(), "ADMINISTRATOR");

        OrderService.OrderView view = service.reject(ORDER, "Sin transporte");

        assertThat(view.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(view.rejectionReason()).isEqualTo("Sin transporte");
    }

    @Test
    void manager_of_another_warehouse_cannot_decide_and_gets_404() {
        givenStoredOrder(OrderStatus.REGISTERED);
        as(UUID.randomUUID(), "WAREHOUSE_MANAGER");

        assertThatThrownBy(() -> service.approve(ORDER)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
        assertThatThrownBy(() -> service.reject(ORDER, "x")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approving_a_cancelled_order_is_409() {
        givenStoredOrder(OrderStatus.CANCELLED);
        as(ROSA, "WAREHOUSE_MANAGER");

        assertThatThrownBy(() -> service.approve(ORDER)).isInstanceOf(InvalidOrderTransitionException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void visibility_of_a_single_order() {
        givenStoredOrder(OrderStatus.REGISTERED);

        as(JORGE, "SITE_MANAGER");
        assertThat(service.get(ORDER).id()).isEqualTo(ORDER);
        as(ROSA, "WAREHOUSE_MANAGER");
        assertThat(service.get(ORDER).id()).isEqualTo(ORDER);
        as(UUID.randomUUID(), "ADMINISTRATOR");
        assertThat(service.get(ORDER).id()).isEqualTo(ORDER);
        as(UUID.randomUUID(), "SITE_MANAGER");
        assertThatThrownBy(() -> service.get(ORDER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_returns_all_for_the_administrator_and_assigned_sites_for_managers() {
        Order stored = storedOrder(OrderStatus.REGISTERED);
        when(orders.findAll()).thenReturn(List.of(stored));
        when(organization.assignedSites(JORGE)).thenReturn(Set.of(TORRE));
        when(orders.findBySites(Set.of(TORRE))).thenReturn(List.of(stored));

        as(UUID.randomUUID(), "ADMINISTRATOR");
        assertThat(service.list()).hasSize(1);
        as(JORGE, "SITE_MANAGER");
        assertThat(service.list()).extracting(OrderService.OrderView::id).containsExactly(ORDER);
    }

    private OrderService.PlaceOrder placeOrder(UUID worksite, String quantity) {
        return new OrderService.PlaceOrder(worksite, CENTRAL, "Urgente",
                List.of(new OrderService.LineRequest(CEMENT, new BigDecimal(quantity))));
    }

    private void givenWarehouse(boolean active) {
        when(organization.findWarehouse(CENTRAL)).thenReturn(Optional.of(
                new WarehouseSnapshot(CENTRAL, "Central", "WAREHOUSE", "Av. 1", active)));
    }

    private void givenMaterial(boolean active) {
        when(organization.findMaterial(CEMENT)).thenReturn(Optional.of(
                new MaterialSnapshot(CEMENT, "CEM-001", "Cemento", "BAG", new BigDecimal("2.50"), active)));
    }

    private void givenStoredOrder(OrderStatus status) {
        when(orders.findById(ORDER)).thenReturn(Optional.of(storedOrder(status)));
    }

    private static Order storedOrder(OrderStatus status) {
        return Order.restore(ORDER, TORRE, CENTRAL, JORGE, null, NOW,
                List.of(OrderLine.request(CEMENT, "CEM-001", "BAG", new BigDecimal("50"))), status, null, null, null, 0L);
    }

    private static Order withId(Order order) {
        if (order.id() != null) {
            return order;
        }
        return Order.restore(ORDER, order.worksiteId(), order.warehouseId(), order.requestedBy(), order.notes(),
                order.placedAt(), order.lines(), order.status(), order.rejectionReason(), order.decidedBy(),
                order.decidedAt(), 0L);
    }

    private static void as(UUID user, String role) {
        TenantContext.set(new TenantInfo(ORG, user, role));
    }
}
