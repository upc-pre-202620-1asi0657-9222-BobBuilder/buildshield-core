package pe.buildshield.core.dispatch.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.audit.AuditTrail;
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
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Despachos: US22 generar, US23 partición del pedido, US24 manifiesto, US27 pesaje de salida y US30
 * transportista. El pedido se lee y actualiza solo por {@link OrderingFacade}; el stock, solo por
 * {@link StockService}; obras, almacenes, materiales y asignaciones, solo por
 * {@link OrganizationContextFacade}.
 *
 * <p>Quién: preparan, completan y despachan el administrador o un encargado asignado al almacén de
 * origen. Ven un despacho el administrador, los encargados del almacén de origen y los de la obra de
 * destino. Para el resto el despacho (o el pedido) no existe: 404.
 */
@Service
public class DispatchService {

    public static final String ORDER_NOT_DISPATCHABLE = "ORDER_NOT_DISPATCHABLE";
    static final String ADMINISTRATOR = "ADMINISTRATOR";

    private final DispatchRepository dispatches;
    private final OrderingFacade ordering;
    private final StockService stock;
    private final OrganizationContextFacade organization;
    private final EvidenceStorage evidence;
    private final ManifestQrCode qrCode;
    private final AuditTrail audit;
    private final Clock clock;
    private final RandomGenerator random;

    public DispatchService(DispatchRepository dispatches, OrderingFacade ordering, StockService stock,
            OrganizationContextFacade organization, EvidenceStorage evidence, ManifestQrCode qrCode, AuditTrail audit,
            Clock clock) {
        this(dispatches, ordering, stock, organization, evidence, qrCode, audit, clock, new SecureRandom());
    }

    DispatchService(DispatchRepository dispatches, OrderingFacade ordering, StockService stock,
            OrganizationContextFacade organization, EvidenceStorage evidence, ManifestQrCode qrCode, AuditTrail audit,
            Clock clock, RandomGenerator random) {
        this.dispatches = dispatches;
        this.ordering = ordering;
        this.stock = stock;
        this.organization = organization;
        this.evidence = evidence;
        this.qrCode = qrCode;
        this.audit = audit;
        this.clock = clock;
        this.random = random;
    }

    /**
     * US22, US23: prepara un despacho de un pedido aprobado. Cada cantidad es mayor que cero y no supera
     * lo pendiente de su línea; lo despachado se suma al pedido en la misma transacción, así dos
     * despachos simultáneos no pueden asignar más de lo pedido.
     */
    @Transactional
    public DispatchView create(CreateDispatch command) {
        OrderSnapshot order = ordering.findOrder(command.orderId())
                .filter(found -> canManage(found.warehouseId()))
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "El pedido no existe"));
        if (!order.dispatchable()) {
            throw new ConflictException(ORDER_NOT_DISPATCHABLE,
                    "Solo se despachan pedidos aprobados con material pendiente; el pedido está " + order.status());
        }
        List<DispatchLine> lines = command.lines() == null ? List.of() : command.lines().stream()
                .map(request -> line(order, request)).toList();
        Dispatch.requireLines(lines);
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        lines.forEach(line -> quantities.put(line.orderLineId(), line.quantity()));
        OrderSnapshot updated = ordering.registerDispatch(order.id(), quantities);
        DispatchType type = updated.hasPending() ? DispatchType.PARTIAL : DispatchType.COMPLETE;
        Instant now = clock.instant();
        Dispatch dispatch = Dispatch.prepare(order.id(), order.warehouseId(), order.worksiteId(), type,
                ManifestCode.generate(now, random), lines, now);
        return audit.recorded("DISPATCH_CREATED", "DISPATCH", DispatchView.of(dispatches.save(dispatch)));
    }

    /** US30: transportista, solo mientras el despacho está Preparado. */
    @Transactional
    public DispatchView assignCarrier(UUID dispatchId, String name, String document, String plate) {
        Dispatch dispatch = manageable(dispatchId);
        dispatch.assignCarrier(new Carrier(name, document, plate));
        return audit.recorded("DISPATCH_CARRIER_ASSIGNED", "DISPATCH", DispatchView.of(dispatches.save(dispatch)));
    }

    /** US27: un solo pesaje de salida, solo mientras el despacho está Preparado. */
    @Transactional
    public DispatchView recordDepartureWeighing(UUID dispatchId, BigDecimal grossKg, BigDecimal tareKg,
            String ticketPhotoUrl) {
        Dispatch dispatch = manageable(dispatchId);
        String evidenceUrl = evidence.registerTicketPhoto(dispatchId, ticketPhotoUrl);
        dispatch.recordDepartureWeighing(DepartureWeighing.of(grossKg, tareKg, evidenceUrl, clock.instant(),
                requester().userId()));
        try {
            return audit.recorded("DEPARTURE_WEIGHING_RECORDED", "DISPATCH", DispatchView.of(dispatches.save(dispatch)));
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_departure_weighings_dispatch")) {
                throw new ConflictException(Dispatch.DEPARTURE_WEIGHING_ALREADY_RECORDED,
                        "El despacho ya tiene su pesaje de salida");
            }
            throw ex;
        }
    }

    /**
     * Salida del almacén (Preparado → EnTransito): exige transportista y pesaje, y consume la reserva de
     * cada línea (RF41: reservado → salida). Todo en una transacción.
     */
    @Transactional
    public DispatchView depart(UUID dispatchId) {
        Dispatch dispatch = manageable(dispatchId);
        dispatch.depart(clock.instant());
        Dispatch departed = dispatches.save(dispatch);
        for (DispatchLine line : departed.lines()) {
            stock.consumeReservation(departed.warehouseId(), line.materialId(), line.quantity(), line.orderLineId(),
                    "Despacho " + departed.manifestCode().value());
        }
        return audit.recorded("DISPATCH_DEPARTED", "DISPATCH", DispatchView.of(departed));
    }

    @Transactional(readOnly = true)
    public DispatchView get(UUID dispatchId) {
        return DispatchView.of(visible(dispatchId));
    }

    /** Despachos visibles para el usuario, opcionalmente de un pedido y en un estado. */
    @Transactional(readOnly = true)
    public List<DispatchView> list(UUID orderId, DispatchStatus status) {
        List<Dispatch> candidates = isAdministrator()
                ? dispatches.findAll()
                : dispatches.findBySites(organization.assignedSites(requester().userId()));
        return candidates.stream()
                .filter(dispatch -> orderId == null || dispatch.orderId().equals(orderId))
                .filter(dispatch -> status == null || dispatch.status() == status)
                .map(DispatchView::of)
                .toList();
    }

    /** US24: manifiesto con pedido, obra, almacén, materiales, transportista, pesaje y el QR del código. */
    @Transactional(readOnly = true)
    public ManifestView manifest(UUID dispatchId) {
        Dispatch dispatch = visible(dispatchId);
        var worksite = organization.findWorksite(dispatch.worksiteId()).orElseThrow(() -> notFound(dispatchId));
        var warehouse = organization.findWarehouse(dispatch.warehouseId()).orElseThrow(() -> notFound(dispatchId));
        Instant orderPlacedAt = ordering.findOrder(dispatch.orderId()).map(OrderSnapshot::placedAt).orElse(null);
        Map<UUID, MaterialSnapshot> materials = organization.findMaterials(
                dispatch.lines().stream().map(DispatchLine::materialId).toList());
        List<ManifestLine> lines = dispatch.lines().stream().map(line -> {
            MaterialSnapshot material = materials.get(line.materialId());
            return new ManifestLine(line.materialId(), material == null ? null : material.sku(),
                    material == null ? null : material.name(), material == null ? null : material.unit(), line.quantity());
        }).toList();
        String code = dispatch.manifestCode().value();
        return new ManifestView(code, DispatchView.of(dispatch), dispatch.orderId(), orderPlacedAt,
                new ManifestSite(worksite.id(), worksite.name(), String.join(", ", worksite.address(),
                        worksite.district(), worksite.city())),
                new ManifestSite(warehouse.id(), warehouse.name(), warehouse.address()), lines,
                Base64.getEncoder().encodeToString(qrCode.png(code)), code);
    }

    private DispatchLine line(OrderSnapshot order, LineRequest request) {
        OrderLineSnapshot orderLine = request.orderLineId() == null ? null : order.line(request.orderLineId()).orElse(null);
        if (orderLine == null) {
            throw new ValidationException("ORDER_LINE_NOT_FOUND", "La línea " + request.orderLineId()
                    + " no pertenece al pedido", List.of(new ErrorDetail("orderLineId", String.valueOf(request.orderLineId()))));
        }
        return DispatchLine.of(orderLine.id(), orderLine.materialId(), request.quantity());
    }

    private Dispatch manageable(UUID dispatchId) {
        return dispatches.findById(dispatchId)
                .filter(dispatch -> canManage(dispatch.warehouseId()))
                .orElseThrow(() -> notFound(dispatchId));
    }

    private Dispatch visible(UUID dispatchId) {
        return dispatches.findById(dispatchId).filter(this::canSee).orElseThrow(() -> notFound(dispatchId));
    }

    private boolean canManage(UUID warehouseId) {
        return isAdministrator() || ("WAREHOUSE_MANAGER".equals(requester().role())
                && organization.isAssigned(requester().userId(), warehouseId));
    }

    private boolean canSee(Dispatch dispatch) {
        if (isAdministrator()) {
            return true;
        }
        UUID user = requester().userId();
        return organization.isAssigned(user, dispatch.warehouseId()) || organization.isAssigned(user, dispatch.worksiteId());
    }

    private static boolean isAdministrator() {
        return ADMINISTRATOR.equals(requester().role());
    }

    private static TenantInfo requester() {
        return TenantContext.require();
    }

    static ResourceNotFoundException notFound(UUID dispatchId) {
        return new ResourceNotFoundException("DISPATCH_NOT_FOUND", "El despacho " + dispatchId + " no existe");
    }

    public record CreateDispatch(UUID orderId, List<LineRequest> lines) {
    }

    public record LineRequest(UUID orderLineId, BigDecimal quantity) {
    }

    public record DispatchView(UUID id, UUID orderId, UUID warehouseId, UUID worksiteId, DispatchType type,
            DispatchStatus status, String manifestCode, Instant preparedAt, Instant departedAt, Instant receivedAt,
            CarrierView carrier, WeighingView departureWeighing, List<LineView> lines) {

        static DispatchView of(Dispatch dispatch) {
            return new DispatchView(dispatch.id(), dispatch.orderId(), dispatch.warehouseId(), dispatch.worksiteId(),
                    dispatch.type(), dispatch.status(), dispatch.manifestCode().value(), dispatch.preparedAt(),
                    dispatch.departedAt(), dispatch.receivedAt(),
                    dispatch.carrier().map(carrier -> new CarrierView(carrier.name(), carrier.document(), carrier.plate()))
                            .orElse(null),
                    dispatch.departureWeighing().map(WeighingView::of).orElse(null),
                    dispatch.lines().stream().map(LineView::of).toList());
        }
    }

    public record CarrierView(String name, String document, String plate) {
    }

    public record WeighingView(BigDecimal grossKg, BigDecimal tareKg, BigDecimal netKg, String ticketPhotoUrl,
            Instant weighedAt, UUID weighedBy) {

        static WeighingView of(DepartureWeighing weighing) {
            return new WeighingView(weighing.grossKg(), weighing.tareKg(), weighing.netKg(), weighing.ticketPhotoUrl(),
                    weighing.weighedAt(), weighing.weighedBy());
        }
    }

    public record LineView(UUID id, UUID orderLineId, UUID materialId, BigDecimal quantity) {

        static LineView of(DispatchLine line) {
            return new LineView(line.id(), line.orderLineId(), line.materialId(), line.quantity());
        }
    }

    public record ManifestView(String manifestCode, DispatchView dispatch, UUID orderId, Instant orderPlacedAt,
            ManifestSite worksite, ManifestSite warehouse, List<ManifestLine> lines, String qrCodePngBase64,
            String qrContent) {
    }

    public record ManifestSite(UUID id, String name, String address) {
    }

    public record ManifestLine(UUID materialId, String sku, String name, String unit, BigDecimal quantity) {
    }
}
