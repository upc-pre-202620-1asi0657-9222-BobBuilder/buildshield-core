package pe.buildshield.core.ordering.application;

import org.springframework.stereotype.Service;
import pe.buildshield.core.ordering.domain.model.Order;
import pe.buildshield.core.ordering.domain.model.OrderRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que los módulos dispatch y reception hacen sobre un pedido: leerlo, registrar lo despachado y lo
 * recibido. Trabaja en la organización del contexto, sin la visibilidad por rol (la decide quien llama).
 *
 * <p>El pedido se guarda con su versión: dos despachos simultáneos del mismo pedido no pueden
 * superar lo pendiente porque el segundo falla por versión (409) o, ya con el primero confirmado, por
 * {@code DISPATCH_EXCEEDS_PENDING}. La restricción de la tabla es la última defensa.
 */
@Service
public class OrderFulfillment {

    private final OrderRepository orders;

    public OrderFulfillment(OrderRepository orders) {
        this.orders = orders;
    }

    public Optional<Order> find(UUID orderId) {
        return orders.findById(orderId);
    }

    public Order registerDispatch(UUID orderId, Map<UUID, BigDecimal> quantitiesByLine) {
        Order order = orders.findById(orderId).orElseThrow(() -> OrderService.notFound(orderId));
        order.registerDispatch(quantitiesByLine);
        return orders.save(order);
    }

    public Order registerReceived(UUID orderId, Map<UUID, BigDecimal> quantitiesByLine) {
        Order order = orders.findById(orderId).orElseThrow(() -> OrderService.notFound(orderId));
        order.registerReceived(quantitiesByLine);
        return orders.save(order);
    }
}
