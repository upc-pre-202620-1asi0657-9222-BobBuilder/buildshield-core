package pe.buildshield.core.ordering;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.ordering.application.OrderFulfillment;
import pe.buildshield.core.ordering.domain.model.Order;
import pe.buildshield.core.ordering.domain.model.OrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fachada pública del módulo ordering para los módulos dispatch y reception. Devuelve copias
 * inmutables (snapshots) y se une a la transacción de quien llama: el despacho o la recepción y el
 * cambio en el pedido se confirman o deshacen juntos.
 *
 * <p>Trabaja dentro de la organización del {@code TenantContext}; no aplica la visibilidad por rol.
 */
@Component
public class OrderingFacade {

    private final OrderFulfillment fulfillment;

    public OrderingFacade(OrderFulfillment fulfillment) {
        this.fulfillment = fulfillment;
    }

    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> findOrder(UUID orderId) {
        return fulfillment.find(orderId).map(OrderSnapshot::of);
    }

    /**
     * Suma lo despachado por línea (id de línea → cantidad).
     *
     * @throws pe.buildshield.core.shared.error.ConflictException   {@code INVALID_ORDER_TRANSITION} si el pedido
     *         no admite despachos, o {@code DISPATCH_EXCEEDS_PENDING} si una cantidad supera lo pendiente
     * @throws pe.buildshield.core.shared.error.ValidationException {@code ORDER_LINE_NOT_FOUND} si una línea no es
     *         del pedido
     */
    @Transactional
    public OrderSnapshot registerDispatch(UUID orderId, Map<UUID, BigDecimal> quantitiesByLine) {
        return OrderSnapshot.of(fulfillment.registerDispatch(orderId, quantitiesByLine));
    }

    /** Suma lo recibido en obra por línea (id de línea → cantidad mayor que cero). */
    @Transactional
    public OrderSnapshot registerReceived(UUID orderId, Map<UUID, BigDecimal> quantitiesByLine) {
        return OrderSnapshot.of(fulfillment.registerReceived(orderId, quantitiesByLine));
    }

    /**
     * @param status      nombre del estado (REGISTERED, IN_REVIEW, PARTIALLY_FULFILLED, …)
     * @param dispatchable si admite despachos (aprobado y con algo pendiente)
     */
    public record OrderSnapshot(UUID id, UUID worksiteId, UUID warehouseId, String status, boolean dispatchable,
            boolean hasPending, Instant placedAt, List<OrderLineSnapshot> lines) {

        static OrderSnapshot of(Order order) {
            return new OrderSnapshot(order.id(), order.worksiteId(), order.warehouseId(), order.status().name(),
                    order.acceptsDispatch(), order.hasPending(), order.placedAt(),
                    order.lines().stream().map(OrderLineSnapshot::of).toList());
        }

        public Optional<OrderLineSnapshot> line(UUID lineId) {
            return lines.stream().filter(line -> line.id().equals(lineId)).findFirst();
        }
    }

    public record OrderLineSnapshot(UUID id, UUID materialId, String sku, String unit, BigDecimal requested,
            BigDecimal dispatched, BigDecimal cancelled, BigDecimal received, BigDecimal pending) {

        static OrderLineSnapshot of(OrderLine line) {
            return new OrderLineSnapshot(line.id(), line.materialId(), line.sku(), line.unit(), line.requested(),
                    line.dispatched(), line.cancelled(), line.received(), line.pending());
        }
    }
}
