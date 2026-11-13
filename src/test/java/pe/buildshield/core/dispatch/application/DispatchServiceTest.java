package pe.buildshield.core.dispatch.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.buildshield.core.dispatch.domain.model.Carrier;
import pe.buildshield.core.dispatch.domain.model.DepartureWeighing;
import pe.buildshield.core.dispatch.domain.model.Dispatch;
import pe.buildshield.core.dispatch.domain.model.DispatchLine;
import pe.buildshield.core.dispatch.domain.model.DispatchRepository;
import pe.buildshield.core.dispatch.domain.model.DispatchStatus;
import pe.buildshield.core.dispatch.domain.model.DispatchType;
import pe.buildshield.core.dispatch.domain.model.ManifestCode;
import pe.buildshield.core.inventory.StockService;
import pe.buildshield.core.ordering.OrderingFacade;
import pe.buildshield.core.ordering.OrderingFacade.OrderLineSnapshot;
import pe.buildshield.core.ordering.OrderingFacade.OrderSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.organization.OrganizationContextFacade.MaterialSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WarehouseSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WorksiteSnapshot;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.support.AuditTestSupport;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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

class DispatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T13:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();
    private static final UUID JORGE = UUID.randomUUID();
    private static final UUID CENTRAL = UUID.randomUUID();
    private static final UUID TORRE = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID LINE = UUID.randomUUID();
    private static final UUID CEMENT = UUID.randomUUID();
    private static final UUID DISPATCH = UUID.randomUUID();

    private final DispatchRepository dispatches = mock(DispatchRepository.class);
    private final OrderingFacade ordering = mock(OrderingFacade.class);
    private final StockService stock = mock(StockService.class);
    private final OrganizationContextFacade organization = mock(OrganizationContextFacade.class);
    private final EvidenceStorage evidence = mock(EvidenceStorage.class);
    private final ManifestQrCode qr = mock(ManifestQrCode.class);
    private final DispatchService service = new DispatchService(dispatches, ordering, stock, organization, evidence, qr,
            AuditTestSupport.noop(), Clock.fixed(NOW, ZoneOffset.UTC), new Random(1));

    @BeforeEach
    void setUp() {
        when(organization.isAssigned(ROSA, CENTRAL)).thenReturn(true);
        when(organization.isAssigned(JORGE, TORRE)).thenReturn(true);
        when(dispatches.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static OrderSnapshot order(String status, boolean dispatchable, boolean hasPending, String pending) {
        return new OrderSnapshot(ORDER, TORRE, CENTRAL, status, dispatchable, hasPending, NOW,
                List.of(new OrderLineSnapshot(LINE, CEMENT, "CEM-001", "BAG", new BigDecimal("50"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(pending))));
    }

    private static Dispatch withId(Dispatch dispatch) {
        if (dispatch.id() != null) {
            return dispatch;
        }
        return Dispatch.restore(DISPATCH, dispatch.orderId(), dispatch.warehouseId(), dispatch.worksiteId(),
                dispatch.type(), dispatch.manifestCode(), dispatch.lines(), dispatch.preparedAt(), dispatch.status(),
                dispatch.carrier().orElse(null), dispatch.departureWeighing().orElse(null), dispatch.departedAt(),
                dispatch.receivedAt(), 0L);
    }

    private static Dispatch stored(DispatchStatus status, Carrier carrier, DepartureWeighing weighing) {
        return Dispatch.restore(DISPATCH, ORDER, CENTRAL, TORRE, DispatchType.PARTIAL,
                new ManifestCode("MAN-20261103-7KQ2M9XA"),
                List.of(new DispatchLine(UUID.randomUUID(), LINE, CEMENT, new BigDecimal("30"))), NOW, status, carrier,
                weighing, null, null, 1L);
    }

    private static void as(UUID user, String role) {
        TenantContext.set(new TenantInfo(ORG, user, role));
    }

    private static DispatchService.CreateDispatch thirty() {
        return new DispatchService.CreateDispatch(ORDER, List.of(new DispatchService.LineRequest(LINE, new BigDecimal("30"))));
    }

    @Test
    void warehouse_manager_dispatches_part_of_an_approved_order() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(order("IN_REVIEW", true, true, "50")));
        when(ordering.registerDispatch(eq(ORDER), any())).thenReturn(order("PARTIALLY_FULFILLED", true, true, "20"));

        DispatchService.DispatchView view = service.create(thirty());

        assertThat(view.id()).isEqualTo(DISPATCH);
        assertThat(view.status()).isEqualTo(DispatchStatus.PREPARED);
        assertThat(view.type()).isEqualTo(DispatchType.PARTIAL);
        assertThat(view.manifestCode()).startsWith("MAN-20261103-");
        assertThat(view.lines()).singleElement().satisfies(line -> {
            assertThat(line.orderLineId()).isEqualTo(LINE);
            assertThat(line.materialId()).isEqualTo(CEMENT);
        });
        verify(ordering).registerDispatch(ORDER, Map.of(LINE, new BigDecimal("30.000")));
    }

    @Test
    void the_dispatch_that_leaves_nothing_pending_is_complete() {
        as(UUID.randomUUID(), "ADMINISTRATOR");
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(order("IN_REVIEW", true, true, "50")));
        when(ordering.registerDispatch(eq(ORDER), any())).thenReturn(order("FULFILLED", false, false, "0"));

        assertThat(service.create(thirty()).type()).isEqualTo(DispatchType.COMPLETE);
    }

    @Test
    void other_warehouse_managers_and_unknown_orders_get_404() {
        as(UUID.randomUUID(), "WAREHOUSE_MANAGER");
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(order("IN_REVIEW", true, true, "50")));
        assertThatThrownBy(() -> service.create(thirty())).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");

        as(JORGE, "SITE_MANAGER");
        assertThatThrownBy(() -> service.create(thirty())).isInstanceOf(ResourceNotFoundException.class);
        verify(ordering, never()).registerDispatch(any(), any());
    }

    @Test
    void an_order_that_is_not_approved_is_not_dispatched() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(order("REGISTERED", false, true, "50")));

        assertThatThrownBy(() -> service.create(thirty())).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", DispatchService.ORDER_NOT_DISPATCHABLE);
    }

    @Test
    void lines_must_belong_to_the_order_and_not_repeat() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(order("IN_REVIEW", true, true, "50")));

        assertThatThrownBy(() -> service.create(new DispatchService.CreateDispatch(ORDER,
                List.of(new DispatchService.LineRequest(UUID.randomUUID(), BigDecimal.ONE)))))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "ORDER_LINE_NOT_FOUND");
        assertThatThrownBy(() -> service.create(new DispatchService.CreateDispatch(ORDER,
                List.of(new DispatchService.LineRequest(null, BigDecimal.ONE)))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(new DispatchService.CreateDispatch(ORDER, List.of(
                new DispatchService.LineRequest(LINE, BigDecimal.ONE), new DispatchService.LineRequest(LINE, BigDecimal.TEN)))))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "DUPLICATE_ORDER_LINE");
        assertThatThrownBy(() -> service.create(new DispatchService.CreateDispatch(ORDER, null)))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "DISPATCH_WITHOUT_LINES");
        verify(ordering, never()).registerDispatch(any(), any());
    }

    @Test
    void carrier_and_weighing_are_recorded_by_the_warehouse_manager() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(dispatches.findById(DISPATCH)).thenReturn(Optional.of(stored(DispatchStatus.PREPARED, null, null)));
        when(evidence.registerTicketPhoto(DISPATCH, "https://evidencias/t.jpg")).thenReturn("https://evidencias/t.jpg");

        DispatchService.DispatchView withCarrier = service.assignCarrier(DISPATCH, "Transportes Rímac", "20555666777", "abc-123");
        assertThat(withCarrier.carrier().plate()).isEqualTo("ABC-123");

        DispatchService.DispatchView weighed = service.recordDepartureWeighing(DISPATCH, new BigDecimal("2500"),
                new BigDecimal("1000"), "https://evidencias/t.jpg");
        assertThat(weighed.departureWeighing().netKg()).isEqualByComparingTo("1500");
        assertThat(weighed.departureWeighing().ticketPhotoUrl()).isEqualTo("https://evidencias/t.jpg");
        assertThat(weighed.departureWeighing().weighedBy()).isEqualTo(ROSA);
    }

    @Test
    void a_concurrent_second_weighing_is_a_conflict() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(dispatches.findById(DISPATCH)).thenAnswer(call -> Optional.of(stored(DispatchStatus.PREPARED, null, null)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uk_departure_weighings_dispatch\""))).when(dispatches).save(any());

        assertThatThrownBy(() -> service.recordDepartureWeighing(DISPATCH, BigDecimal.TEN, BigDecimal.ONE, null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Dispatch.DEPARTURE_WEIGHING_ALREADY_RECORDED);

        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("x", new SQLException("other"))).when(dispatches).save(any());
        assertThatThrownBy(() -> service.recordDepartureWeighing(DISPATCH, BigDecimal.TEN, BigDecimal.ONE, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void departing_consumes_the_reservation_of_each_line() {
        as(ROSA, "WAREHOUSE_MANAGER");
        when(dispatches.findById(DISPATCH)).thenReturn(Optional.of(stored(DispatchStatus.PREPARED,
                new Carrier("Rímac", "20555666777", "ABC-123"),
                DepartureWeighing.of(BigDecimal.TEN, BigDecimal.ONE, null, NOW, ROSA))));

        DispatchService.DispatchView departed = service.depart(DISPATCH);

        assertThat(departed.status()).isEqualTo(DispatchStatus.IN_TRANSIT);
        assertThat(departed.departedAt()).isEqualTo(NOW);
        verify(stock).consumeReservation(eq(CENTRAL), eq(CEMENT), eq(new BigDecimal("30.000")), eq(LINE), anyString());
    }

    @Test
    void only_the_origin_warehouse_manages_and_only_involved_sites_see() {
        when(dispatches.findById(DISPATCH)).thenReturn(Optional.of(stored(DispatchStatus.PREPARED, null, null)));

        as(JORGE, "SITE_MANAGER");
        assertThat(service.get(DISPATCH).id()).isEqualTo(DISPATCH);
        assertThatThrownBy(() -> service.depart(DISPATCH)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "DISPATCH_NOT_FOUND");
        as(UUID.randomUUID(), "WAREHOUSE_MANAGER");
        assertThatThrownBy(() -> service.get(DISPATCH)).isInstanceOf(ResourceNotFoundException.class);
        as(UUID.randomUUID(), "ADMINISTRATOR");
        assertThat(service.get(DISPATCH).id()).isEqualTo(DISPATCH);
    }

    @Test
    void lists_by_visibility_order_and_status() {
        Dispatch prepared = stored(DispatchStatus.PREPARED, null, null);
        when(dispatches.findAll()).thenReturn(List.of(prepared));
        when(organization.assignedSites(JORGE)).thenReturn(Set.of(TORRE));
        when(dispatches.findBySites(Set.of(TORRE))).thenReturn(List.of(prepared));

        as(UUID.randomUUID(), "ADMINISTRATOR");
        assertThat(service.list(null, null)).hasSize(1);
        assertThat(service.list(ORDER, DispatchStatus.PREPARED)).hasSize(1);
        assertThat(service.list(UUID.randomUUID(), null)).isEmpty();
        assertThat(service.list(null, DispatchStatus.IN_TRANSIT)).isEmpty();
        as(JORGE, "SITE_MANAGER");
        assertThat(service.list(ORDER, null)).extracting(DispatchService.DispatchView::id).containsExactly(DISPATCH);
    }

    @Test
    void manifest_has_sites_materials_and_the_qr_of_its_code() {
        as(JORGE, "SITE_MANAGER");
        when(dispatches.findById(DISPATCH)).thenReturn(Optional.of(stored(DispatchStatus.PREPARED, null, null)));
        when(organization.findWorksite(TORRE)).thenReturn(Optional.of(new WorksiteSnapshot(TORRE, "Torre Norte",
                "Av. Principal 100", "Miraflores", "Lima", LocalDate.of(2026, 11, 1), null)));
        when(organization.findWarehouse(CENTRAL)).thenReturn(Optional.of(new WarehouseSnapshot(CENTRAL, "Almacén Central",
                "WAREHOUSE", "Av. Argentina 2500", true)));
        when(organization.findMaterials(any())).thenReturn(Map.of(CEMENT, new MaterialSnapshot(CEMENT, "CEM-001",
                "Cemento Portland tipo I", "BAG", new BigDecimal("2.50"), true)));
        when(ordering.findOrder(ORDER)).thenReturn(Optional.of(order("PARTIALLY_FULFILLED", true, true, "20")));
        when(qr.png("MAN-20261103-7KQ2M9XA")).thenReturn(new byte[]{1, 2, 3});

        DispatchService.ManifestView manifest = service.manifest(DISPATCH);

        assertThat(manifest.manifestCode()).isEqualTo("MAN-20261103-7KQ2M9XA");
        assertThat(manifest.qrContent()).isEqualTo("MAN-20261103-7KQ2M9XA");
        assertThat(Base64.getDecoder().decode(manifest.qrCodePngBase64())).containsExactly(1, 2, 3);
        assertThat(manifest.worksite().address()).isEqualTo("Av. Principal 100, Miraflores, Lima");
        assertThat(manifest.warehouse().name()).isEqualTo("Almacén Central");
        assertThat(manifest.orderPlacedAt()).isEqualTo(NOW);
        assertThat(manifest.lines()).singleElement().satisfies(line -> {
            assertThat(line.sku()).isEqualTo("CEM-001");
            assertThat(line.name()).isEqualTo("Cemento Portland tipo I");
            assertThat(line.unit()).isEqualTo("BAG");
        });

        when(organization.findMaterials(any())).thenReturn(Map.of());
        assertThat(service.manifest(DISPATCH).lines()).singleElement().satisfies(line -> assertThat(line.sku()).isNull());
        when(organization.findWorksite(TORRE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.manifest(DISPATCH)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void receipts_mark_the_dispatch_received() {
        as(JORGE, "SITE_MANAGER");
        when(dispatches.findById(DISPATCH)).thenReturn(Optional.of(stored(DispatchStatus.IN_TRANSIT, null, null)));
        DispatchReceipts receipts = new DispatchReceipts(dispatches, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(receipts.find(DISPATCH)).isPresent();
        assertThat(receipts.markReceived(DISPATCH).status()).isEqualTo(DispatchStatus.RECEIVED);
        when(dispatches.findById(DISPATCH)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> receipts.markReceived(DISPATCH)).isInstanceOf(ResourceNotFoundException.class);
    }
}
