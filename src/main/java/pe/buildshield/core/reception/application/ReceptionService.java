package pe.buildshield.core.reception.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.audit.AuditTrail;
import pe.buildshield.core.dispatch.DispatchFacade;
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
import pe.buildshield.core.reception.domain.model.Shrinkage;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recepción en obra: US35 cotejo (lo pedido, despachado y recibido, con la merma contra la tolerancia
 * del material, QAS01) y US37 conformidad (QAS10: una sola vez, con efectos en la misma transacción).
 *
 * <p>Quién: abren, registran y confirman el administrador o un encargado asignado a la obra de destino.
 * Consultan además los encargados del almacén de origen. Para el resto la recepción no existe: 404.
 */
@Service
public class ReceptionService {

    public static final String RECEPTION_ALREADY_EXISTS = "RECEPTION_ALREADY_EXISTS";
    public static final String DISPATCH_NOT_IN_TRANSIT = "DISPATCH_NOT_IN_TRANSIT";
    static final String ADMINISTRATOR = "ADMINISTRATOR";

    private final ReceptionRepository receptions;
    private final DispatchFacade dispatches;
    private final OrderingFacade ordering;
    private final StockService stock;
    private final OrganizationContextFacade organization;
    private final AuditTrail audit;
    private final Clock clock;

    public ReceptionService(ReceptionRepository receptions, DispatchFacade dispatches, OrderingFacade ordering,
            StockService stock, OrganizationContextFacade organization, AuditTrail audit, Clock clock) {
        this.receptions = receptions;
        this.dispatches = dispatches;
        this.ordering = ordering;
        this.stock = stock;
        this.organization = organization;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Abre la recepción de un despacho en tránsito hacia una obra del usuario. Hay una sola por despacho:
     * si ya existe responde 409 {@code RECEPTION_ALREADY_EXISTS} con su id en los detalles.
     */
    @Transactional
    public ReceptionView start(UUID dispatchId) {
        DispatchSnapshot dispatch = dispatches.findDispatch(dispatchId)
                .filter(found -> canWork(found.worksiteId()))
                .orElseThrow(() -> new ResourceNotFoundException("DISPATCH_NOT_FOUND", "El despacho no existe"));
        receptions.findIdByDispatchId(dispatchId).ifPresent(existing -> {
            throw alreadyExists(existing);
        });
        if (!dispatch.inTransit()) {
            throw new ConflictException(DISPATCH_NOT_IN_TRANSIT,
                    "Solo se recibe un despacho en tránsito; el despacho está " + dispatch.status());
        }
        List<ReceptionLine> lines = dispatch.lines().stream()
                .map(line -> ReceptionLine.expect(line.id(), line.orderLineId(), line.materialId(), line.quantity()))
                .toList();
        Reception reception = Reception.start(dispatch.id(), dispatch.orderId(), dispatch.warehouseId(),
                dispatch.worksiteId(), lines);
        try {
            return audit.recorded("RECEPTION_STARTED", "RECEPTION", ReceptionView.of(receptions.save(reception)));
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_receptions_dispatch")) {
                throw alreadyExists(null);
            }
            throw ex;
        }
    }

    /** US35: registra lo recibido de una línea; se puede corregir mientras la recepción está en curso. */
    @Transactional
    public ReceptionView recordLine(UUID receptionId, UUID lineId, BigDecimal receivedQty) {
        Reception reception = workable(receptionId);
        reception.recordReceived(lineId, receivedQty);
        return ReceptionView.of(receptions.save(reception));
    }

    @Transactional(readOnly = true)
    public ReceptionView get(UUID receptionId) {
        return ReceptionView.of(visible(receptionId));
    }

    /**
     * US35, QAS01: por material, lo solicitado (pedido), despachado, recibido, la diferencia, la merma y si
     * está dentro de la tolerancia del material. Usa un número fijo de consultas, sin importar cuántas
     * líneas tenga: la recepción con sus líneas, el pedido con sus líneas y los materiales de una vez.
     */
    @Transactional(readOnly = true)
    public ComparisonView comparison(UUID receptionId) {
        Reception reception = visible(receptionId);
        OrderSnapshot order = ordering.findOrder(reception.orderId()).orElseThrow(() -> notFound(receptionId));
        Map<UUID, MaterialSnapshot> materials = organization.findMaterials(
                reception.lines().stream().map(ReceptionLine::materialId).toList());
        List<ComparisonLine> lines = reception.lines().stream().map(line -> {
            MaterialSnapshot material = materials.get(line.materialId());
            BigDecimal requested = order.line(line.orderLineId()).map(OrderLineSnapshot::requested).orElse(null);
            BigDecimal tolerance = material == null ? BigDecimal.ZERO : material.wasteTolerancePercent();
            Boolean within = line.isRecorded() ? Shrinkage.withinTolerance(line.shrinkagePercent(), tolerance) : null;
            return new ComparisonLine(line.id(), line.materialId(), material == null ? null : material.sku(),
                    material == null ? null : material.name(), material == null ? null : material.unit(), requested,
                    line.dispatchedQty(), line.receivedQty(), line.difference(), line.shrinkagePercent(), tolerance,
                    within);
        }).toList();
        boolean complete = lines.stream().allMatch(line -> line.received() != null);
        boolean allWithin = complete && lines.stream().allMatch(line -> Boolean.TRUE.equals(line.withinTolerance()));
        return new ComparisonView(reception.id(), reception.dispatchId(), reception.orderId(), reception.status(),
                complete, allWithin, lines);
    }

    /**
     * US37, QAS10: conformidad. Exige lo recibido de todas las líneas y, en una sola transacción, suma lo
     * recibido al stock de la obra, lo registra en el pedido, marca la recepción Confirmada y el despacho
     * Recibido. Una segunda conformidad responde 409 {@code RECEPTION_ALREADY_CONFIRMED}.
     */
    @Transactional
    public ReceptionView confirm(UUID receptionId) {
        Reception reception = workable(receptionId);
        reception.confirm(requester().userId(), clock.instant());
        Reception confirmed = receptions.save(reception);
        Map<UUID, BigDecimal> receivedByOrderLine = new LinkedHashMap<>();
        for (ReceptionLine line : confirmed.lines()) {
            if (line.receivedQty().signum() > 0) {
                stock.add(confirmed.worksiteId(), line.materialId(), line.receivedQty(), "Recepción " + confirmed.id());
                receivedByOrderLine.merge(line.orderLineId(), line.receivedQty(), BigDecimal::add);
            }
        }
        if (!receivedByOrderLine.isEmpty()) {
            ordering.registerReceived(confirmed.orderId(), receivedByOrderLine);
        }
        dispatches.markReceived(confirmed.dispatchId());
        return audit.recorded("RECEPTION_CONFIRMED", "RECEPTION", ReceptionView.of(confirmed));
    }

    private Reception workable(UUID receptionId) {
        return receptions.findById(receptionId).filter(reception -> canWork(reception.worksiteId()))
                .orElseThrow(() -> notFound(receptionId));
    }

    private Reception visible(UUID receptionId) {
        return receptions.findById(receptionId)
                .filter(reception -> canWork(reception.worksiteId())
                        || organization.isAssigned(requester().userId(), reception.warehouseId()))
                .orElseThrow(() -> notFound(receptionId));
    }

    /** Administrador, o encargado de obra asignado a la obra de destino. */
    private boolean canWork(UUID worksiteId) {
        return isAdministrator() || ("SITE_MANAGER".equals(requester().role())
                && organization.isAssigned(requester().userId(), worksiteId));
    }

    private static boolean isAdministrator() {
        return ADMINISTRATOR.equals(requester().role());
    }

    private static TenantInfo requester() {
        return TenantContext.require();
    }

    private static ConflictException alreadyExists(UUID existing) {
        return new ConflictException(RECEPTION_ALREADY_EXISTS, "El despacho ya tiene una recepción",
                existing == null ? List.of() : List.of(new ErrorDetail("receptionId", existing.toString())));
    }

    static ResourceNotFoundException notFound(UUID receptionId) {
        return new ResourceNotFoundException("RECEPTION_NOT_FOUND", "La recepción " + receptionId + " no existe");
    }

    public record ReceptionView(UUID id, UUID dispatchId, UUID orderId, UUID warehouseId, UUID worksiteId,
            ReceptionStatus status, UUID confirmedBy, Instant confirmedAt, List<LineView> lines) {

        static ReceptionView of(Reception reception) {
            return new ReceptionView(reception.id(), reception.dispatchId(), reception.orderId(),
                    reception.warehouseId(), reception.worksiteId(), reception.status(), reception.confirmedBy(),
                    reception.confirmedAt(), reception.lines().stream().map(LineView::of).toList());
        }
    }

    public record LineView(UUID id, UUID dispatchLineId, UUID orderLineId, UUID materialId, BigDecimal dispatchedQty,
            BigDecimal receivedQty, BigDecimal shrinkagePercent) {

        static LineView of(ReceptionLine line) {
            return new LineView(line.id(), line.dispatchLineId(), line.orderLineId(), line.materialId(),
                    line.dispatchedQty(), line.receivedQty(), line.shrinkagePercent());
        }
    }

    public record ComparisonView(UUID receptionId, UUID dispatchId, UUID orderId, ReceptionStatus status,
            boolean complete, boolean withinTolerance, List<ComparisonLine> lines) {
    }

    /**
     * @param difference      despachado - recibido (lo que faltó); {@code null} si aún no se registra lo recibido
     * @param withinTolerance merma ≤ tolerancia del material; {@code null} si aún no se registra lo recibido
     */
    public record ComparisonLine(UUID lineId, UUID materialId, String sku, String materialName, String unit,
            BigDecimal requested, BigDecimal dispatched, BigDecimal received, BigDecimal difference,
            BigDecimal shrinkagePercent, BigDecimal tolerancePercent, Boolean withinTolerance) {
    }
}
