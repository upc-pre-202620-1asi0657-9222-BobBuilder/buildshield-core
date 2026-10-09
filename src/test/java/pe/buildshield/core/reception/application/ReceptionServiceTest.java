package pe.buildshield.core.reception.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.buildshield.core.dispatch.DispatchFacade;
import pe.buildshield.core.dispatch.DispatchFacade.DispatchLineSnapshot;
import pe.buildshield.core.dispatch.DispatchFacade.DispatchSnapshot;
import pe.buildshield.core.inventory.StockService;
import pe.buildshield.core.ordering.OrderingFacade;
import pe.buildshield.core.ordering.OrderingFacade.OrderLineSnapshot;
import pe.buildshield.core.ordering.OrderingFacade.OrderSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.organization.OrganizationContextFacade.MaterialSnapshot;
import pe.buildshield.core.reception.domain.model.Reception;
import pe.buildshield.core.reception.domain.model.ReceptionLine;
import pe.buildshield.core.reception.domain.model.ReceptionRepository;
import pe.buildshield.core.reception.domain.model.ReceptionStatus;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.support.AuditTestSupport;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T16:30:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID JORGE = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();
    private static final UUID TORRE = UUID.randomUUID();
    private static final UUID CENTRAL = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID DISPATCH = UUID.randomUUID();
    private static final UUID RECEPTION = UUID.randomUUID();
    private static final UUID CEMENT = UUID.randomUUID();
    private static final UUID STEEL = UUID.randomUUID();
    private static final UUID CEMENT_ORDER_LINE = UUID.randomUUID();
    private static final UUID STEEL_ORDER_LINE = UUID.randomUUID();
    private static final UUID CEMENT_LINE = UUID.randomUUID();
    private static final UUID STEEL_LINE = UUID.randomUUID();

    private final ReceptionRepository receptions = mock(ReceptionRepository.class);
    private final DispatchFacade dispatches = mock(DispatchFacade.class);
    private final OrderingFacade ordering = mock(OrderingFacade.class);
    private final StockService stock = mock(StockService.class);
    private final OrganizationContextFacade organization = mock(OrganizationContextFacade.class);
    private final ReceptionService service = new ReceptionService(receptions, dispatches, ordering, stock, organization,
            AuditTestSupport.noop(), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(organization.isAssigned(JORGE, TORRE)).thenReturn(true);
        when(organization.isAssigned(ROSA, CENTRAL)).thenReturn(true);
        when(receptions.save(any())).thenAnswer(invocation -> withIds(invocation.getArgument(0)));
        when(receptions.findIdByDispatchId(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static void as(UUID user, String role) {
        TenantContext.set(new TenantInfo(ORG, user, role));
    }

    private static DispatchSnapshot dispatch(String status) {
        return new DispatchSnapshot(DISPATCH, ORDER, CENTRAL, TORRE, status, "MAN-20261103-7KQ2M9XA", List.of(
                new DispatchLineSnapshot(UUID.randomUUID(), CEMENT_ORDER_LINE, CEMENT, new BigDecimal("50")),
                new DispatchLineSnapshot(UUID.randomUUID(), STEEL_ORDER_LINE, STEEL, new BigDecimal("120"))));
    }

    private static Reception withIds(Reception reception) {
        if (reception.id() != null) {
            return reception;
        }
        List<ReceptionLine> lines = reception.lines().stream().map(line -> ReceptionLine.restore(
                line.materialId().equals(CEMENT) ? CEMENT_LINE : STEEL_LINE, line.dispatchLineId(), line.orderLineId(),
                line.materialId(), line.dispatchedQty(), line.receivedQty(), line.shrinkagePercent())).toList();
        return Reception.restore(RECEPTION, reception.dispatchId(), reception.orderId(), reception.warehouseId(),
                reception.worksiteId(), lines, reception.status(), null, null, 0L);
    }

    private static Reception stored(String cementReceived, String steelReceived) {
        Reception reception = Reception.restore(RECEPTION, DISPATCH, ORDER, CENTRAL, TORRE, List.of(
                ReceptionLine.restore(CEMENT_LINE, UUID.randomUUID(), CEMENT_ORDER_LINE, CEMENT, new BigDecimal("50"),
                        null, null),
                ReceptionLine.restore(STEEL_LINE, UUID.randomUUID(), STEEL_ORDER_LINE, STEEL, new BigDecimal("120"),
                        null, null)), ReceptionStatus.IN_PROGRESS, null, null, 1L);
        if (cementReceived != null) {
            reception.recordReceived(CEMENT_LINE, new BigDecimal(cementReceived));
        }
        if (steelReceived != null) {
            reception.recordReceived(STEEL_LINE, new BigDecimal(steelReceived));
        }
        return reception;
    }

    @Test
    void the_site_manager_starts_the_reception_of_a_dispatch_in_transit() {
        as(JORGE, "SITE_MANAGER");
        when(dispatches.findDispatch(DISPATCH)).thenReturn(Optional.of(dispatch("IN_TRANSIT")));

        ReceptionService.ReceptionView view = service.start(DISPATCH);

        assertThat(view.id()).isEqualTo(RECEPTION);
        assertThat(view.status()).isEqualTo(ReceptionStatus.IN_PROGRESS);
        assertThat(view.lines()).hasSize(2).allSatisfy(line -> assertThat(line.receivedQty()).isNull());
    }

    @Test
    void a_second_reception_of_the_same_dispatch_is_409_with_the_existing_id() {
        as(JORGE, "SITE_MANAGER");
        when(dispatches.findDispatch(DISPATCH)).thenReturn(Optional.of(dispatch("IN_TRANSIT")));
        when(receptions.findIdByDispatchId(DISPATCH)).thenReturn(Optional.of(RECEPTION));

        assertThatThrownBy(() -> service.start(DISPATCH)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", ReceptionService.RECEPTION_ALREADY_EXISTS)
                .satisfies(ex -> assertThat(((ConflictException) ex).getDetails()).singleElement()
                        .satisfies(detail -> assertThat(detail.message()).isEqualTo(RECEPTION.toString())));

        when(receptions.findIdByDispatchId(DISPATCH)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uk_receptions_dispatch\""))).when(receptions).save(any());
        assertThatThrownBy(() -> service.start(DISPATCH)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", ReceptionService.RECEPTION_ALREADY_EXISTS);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("x", new SQLException("otra"))).when(receptions).save(any());
        assertThatThrownBy(() -> service.start(DISPATCH)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void only_dispatches_in_transit_to_my_worksites_are_received() {
        as(JORGE, "SITE_MANAGER");
        when(dispatches.findDispatch(DISPATCH)).thenReturn(Optional.of(dispatch("PREPARED")));
        assertThatThrownBy(() -> service.start(DISPATCH)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", ReceptionService.DISPATCH_NOT_IN_TRANSIT);

        as(UUID.randomUUID(), "SITE_MANAGER");
        assertThatThrownBy(() -> service.start(DISPATCH)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "DISPATCH_NOT_FOUND");
        as(ROSA, "WAREHOUSE_MANAGER");
        assertThatThrownBy(() -> service.start(DISPATCH)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void records_a_line_and_compares_against_the_tolerance() {
        as(JORGE, "SITE_MANAGER");
        when(receptions.findById(RECEPTION)).thenReturn(Optional.of(stored(null, "110")));
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(new OrderSnapshot(ORDER, TORRE, CENTRAL,
                "FULFILLED", false, false, NOW, List.of(
                new OrderLineSnapshot(CEMENT_ORDER_LINE, CEMENT, "CEM-001", "BAG", new BigDecimal("60"), new BigDecimal("50"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN),
                new OrderLineSnapshot(STEEL_ORDER_LINE, STEEL, "FIE-012", "UNIT", new BigDecimal("120"),
                        new BigDecimal("120"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))));
        when(organization.findMaterials(any())).thenReturn(Map.of(
                CEMENT, new MaterialSnapshot(CEMENT, "CEM-001", "Cemento", "BAG", new BigDecimal("2.50"), true),
                STEEL, new MaterialSnapshot(STEEL, "FIE-012", "Fierro", "UNIT", new BigDecimal("1.00"), true)));

        ReceptionService.ComparisonView pending = service.comparison(RECEPTION);
        assertThat(pending.complete()).isFalse();
        assertThat(pending.withinTolerance()).isFalse();

        service.recordLine(RECEPTION, CEMENT_LINE, new BigDecimal("49"));
        ReceptionService.ComparisonView comparison = service.comparison(RECEPTION);

        assertThat(comparison.complete()).isTrue();
        assertThat(comparison.withinTolerance()).isFalse();
        assertThat(comparison.lines()).filteredOn(line -> line.sku().equals("CEM-001")).singleElement().satisfies(line -> {
            assertThat(line.requested()).isEqualByComparingTo("60");
            assertThat(line.dispatched()).isEqualByComparingTo("50");
            assertThat(line.received()).isEqualByComparingTo("49");
            assertThat(line.difference()).isEqualByComparingTo("1");
            assertThat(line.shrinkagePercent()).isEqualByComparingTo("2.00");
            assertThat(line.withinTolerance()).isTrue();
        });
        assertThat(comparison.lines()).filteredOn(line -> line.sku().equals("FIE-012")).singleElement()
                .satisfies(line -> assertThat(line.withinTolerance()).isFalse());

        as(ROSA, "WAREHOUSE_MANAGER");
        assertThat(service.get(RECEPTION).id()).isEqualTo(RECEPTION);
        assertThatThrownBy(() -> service.recordLine(RECEPTION, CEMENT_LINE, BigDecimal.ONE))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "RECEPTION_NOT_FOUND");
    }

    @Test
    void comparison_without_the_order_or_catalogue_entry_still_answers() {
        as(UUID.randomUUID(), "ADMINISTRATOR");
        when(receptions.findById(RECEPTION)).thenReturn(Optional.of(stored("50", null)));
        when(ordering.findOrder(ORDER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.comparison(RECEPTION)).isInstanceOf(ResourceNotFoundException.class);

        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(new OrderSnapshot(ORDER, TORRE, CENTRAL, "FULFILLED",
                false, false, NOW, List.of())));
        when(organization.findMaterials(any())).thenReturn(Map.of());
        assertThat(service.comparison(RECEPTION).lines()).allSatisfy(line -> {
            assertThat(line.sku()).isNull();
            assertThat(line.requested()).isNull();
            assertThat(line.tolerancePercent()).isZero();
        });
    }

    @Test
    void confirmation_adds_worksite_stock_registers_the_order_and_receives_the_dispatch() {
        as(JORGE, "SITE_MANAGER");
        when(receptions.findById(RECEPTION)).thenReturn(Optional.of(stored("49", "0")));

        ReceptionService.ReceptionView confirmed = service.confirm(RECEPTION);

        assertThat(confirmed.status()).isEqualTo(ReceptionStatus.CONFIRMED);
        assertThat(confirmed.confirmedBy()).isEqualTo(JORGE);
        assertThat(confirmed.confirmedAt()).isEqualTo(NOW);
        verify(stock).add(eq(TORRE), eq(CEMENT), eq(new BigDecimal("49.000")), anyString());
        verify(stock, never()).add(eq(TORRE), eq(STEEL), any(), anyString());
        verify(ordering).registerReceived(ORDER, Map.of(CEMENT_ORDER_LINE, new BigDecimal("49.000")));
        verify(dispatches).markReceived(DISPATCH);
    }

    @Test
    void nothing_received_still_closes_the_dispatch_without_touching_stock() {
        as(UUID.randomUUID(), "ADMINISTRATOR");
        when(receptions.findById(RECEPTION)).thenReturn(Optional.of(stored("0", "0")));

        service.confirm(RECEPTION);

        verify(stock, never()).add(any(), any(), any(), anyString());
        verify(ordering, never()).registerReceived(any(), any());
        verify(dispatches).markReceived(DISPATCH);
    }

    @Test
    void an_incomplete_or_already_confirmed_reception_is_not_confirmed() {
        as(JORGE, "SITE_MANAGER");
        when(receptions.findById(RECEPTION)).thenReturn(Optional.of(stored("49", null)));
        assertThatThrownBy(() -> service.confirm(RECEPTION)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Reception.RECEPTION_INCOMPLETE);

        Reception confirmed = stored("49", "120");
        confirmed.confirm(JORGE, NOW);
        when(receptions.findById(RECEPTION)).thenReturn(Optional.of(confirmed));
        assertThatThrownBy(() -> service.confirm(RECEPTION)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Reception.RECEPTION_ALREADY_CONFIRMED);
        verify(stock, never()).add(any(), any(), any(), anyString());
    }
}
