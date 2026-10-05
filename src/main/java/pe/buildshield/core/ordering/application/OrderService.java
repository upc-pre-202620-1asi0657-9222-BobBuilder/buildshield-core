package pe.buildshield.core.ordering.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.ordering.domain.model.Order;
import pe.buildshield.core.ordering.domain.model.OrderLine;
import pe.buildshield.core.ordering.domain.model.OrderRepository;
import pe.buildshield.core.ordering.domain.model.OrderStatus;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.organization.OrganizationContextFacade.MaterialSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WarehouseSnapshot;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * US18 crear pedido, US20 aprobar o rechazar y US21 consultar estado. Obra, almacén, materiales y
 * asignaciones se consultan solo por {@link OrganizationContextFacade}.
 *
 * <p>Visibilidad: el administrador ve todos los pedidos; el encargado de obra, los de sus obras; el
 * encargado de almacén, los de sus almacenes. Lo demás responde 404.
 */
@Service
public class OrderService {

    static final String ADMINISTRATOR = "ADMINISTRATOR";

    private final AuditTrail audit;
    private final OrderRepository orders;
    private final OrganizationContextFacade organization;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrganizationContextFacade organization, Clock clock, AuditTrail audit) {
        this.orders = orders;
        this.organization = organization;
        this.clock = clock;
        this.audit = audit;
    }

    /** US18: el encargado de obra pide materiales para una obra asignada a un almacén activo. */
    @Transactional
    public OrderView place(PlaceOrder command) {
        TenantInfo requester = requester();
        organization.findWorksite(command.worksiteId())
                .filter(worksite -> organization.isAssigned(requester.userId(), worksite.id()))
                .orElseThrow(() -> new ResourceNotFoundException("WORKSITE_NOT_FOUND", "La obra no existe"));
        WarehouseSnapshot warehouse = organization.findWarehouse(command.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "El almacén no existe"));
        if (!warehouse.active()) {
            throw new ConflictException("SITE_INACTIVE", "El almacén está desactivado: no recibe pedidos");
        }
        List<OrderLine> lines = command.lines().stream().map(this::line).toList();
        Order order = Order.place(command.worksiteId(), command.warehouseId(), requester.userId(), command.notes(),
                lines, clock.instant());
        return audit.recorded("ORDER_CREATED", "ORDER", OrderView.of(orders.save(order)));
    }

    /** US20: aprueba el encargado del almacén de origen o el administrador. */
    @Transactional
    public OrderView approve(UUID orderId) {
        Order order = decidableOrder(orderId);
        order.approve(requester().userId(), clock.instant());
        return audit.recorded("ORDER_APPROVED", "ORDER", OrderView.of(orders.save(order)));
    }

    /** US20: rechazo con motivo obligatorio; el pedido queda Cancelado. */
    @Transactional
    public OrderView reject(UUID orderId, String reason) {
        Order order = decidableOrder(orderId);
        order.reject(requester().userId(), reason, clock.instant());
        return audit.recorded("ORDER_REJECTED", "ORDER", OrderView.of(orders.save(order)));
    }

    /** US21: estado y cantidades por material. */
    @Transactional(readOnly = true)
    public OrderView get(UUID orderId) {
        return OrderView.of(visibleOrder(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderView> list() {
        List<Order> visible = isAdministrator()
                ? orders.findAll()
                : orders.findBySites(organization.assignedSites(requester().userId()));
        return visible.stream().map(OrderView::of).toList();
    }

    private OrderLine line(LineRequest request) {
        MaterialSnapshot material = organization.findMaterial(request.materialId())
                .orElseThrow(() -> new ResourceNotFoundException("MATERIAL_NOT_FOUND", "El material no existe"));
        if (!material.active()) {
            throw new ConflictException("MATERIAL_INACTIVE", "El material " + material.sku() + " está retirado del catálogo");
        }
        return OrderLine.request(material.id(), material.sku(), material.unit(), request.quantity());
    }

    private Order visibleOrder(UUID orderId) {
        return orders.findById(orderId).filter(this::canSee).orElseThrow(() -> notFound(orderId));
    }

    /** Solo el administrador o un encargado asignado al almacén de origen; para el resto el pedido no existe. */
    private Order decidableOrder(UUID orderId) {
        return orders.findById(orderId)
                .filter(order -> isAdministrator() || organization.isAssigned(requester().userId(), order.warehouseId()))
                .orElseThrow(() -> notFound(orderId));
    }

    private boolean canSee(Order order) {
        if (isAdministrator()) {
            return true;
        }
        UUID user = requester().userId();
        return organization.isAssigned(user, order.worksiteId()) || organization.isAssigned(user, order.warehouseId());
    }

    private static boolean isAdministrator() {
        return ADMINISTRATOR.equals(requester().role());
    }

    private static TenantInfo requester() {
        return TenantContext.require();
    }

    static ResourceNotFoundException notFound(UUID orderId) {
        return new ResourceNotFoundException("ORDER_NOT_FOUND", "El pedido " + orderId + " no existe");
    }

    public record PlaceOrder(UUID worksiteId, UUID warehouseId, String notes, List<LineRequest> lines) {
    }

    public record LineRequest(UUID materialId, BigDecimal quantity) {
    }

    public record OrderView(UUID id, UUID worksiteId, UUID warehouseId, UUID requestedBy, OrderStatus status,
            String notes, String rejectionReason, Instant placedAt, UUID decidedBy, Instant decidedAt,
            List<LineView> lines) {

        static OrderView of(Order order) {
            return new OrderView(order.id(), order.worksiteId(), order.warehouseId(), order.requestedBy(),
                    order.status(), order.notes(), order.rejectionReason(), order.placedAt(), order.decidedBy(),
                    order.decidedAt(), order.lines().stream().map(LineView::of).toList());
        }
    }

    public record LineView(UUID materialId, String sku, String unit, BigDecimal requested, BigDecimal dispatched,
            BigDecimal cancelled, BigDecimal received, BigDecimal pending) {

        static LineView of(OrderLine line) {
            return new LineView(line.materialId(), line.sku(), line.unit(), line.requested(), line.dispatched(),
                    line.cancelled(), line.received(), line.pending());
        }
    }
}
