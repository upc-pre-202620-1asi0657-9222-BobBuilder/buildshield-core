package pe.buildshield.core.inventory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.inventory.application.StockOperations;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fachada pública del módulo inventory: única forma en que otros módulos (pedidos, despachos y
 * recepciones) mueven stock. Se une a la transacción de quien llama, así el descuento y el resto del despacho se
 * confirman o deshacen juntos.
 */
@Component
public class StockService {

    private final StockOperations operations;

    public StockService(StockOperations operations) {
        this.operations = operations;
    }

    /**
     * Descuenta stock con bloqueo optimista (hasta 3 reintentos ante conflicto de versión).
     *
     * @throws InsufficientStockException          si no alcanza; no modifica nada
     * @throws ConcurrentStockModificationException si se agotaron los reintentos
     */
    @Transactional
    public StockLevel deduct(UUID locationId, UUID materialId, BigDecimal quantity) {
        return operations.deduct(locationId, materialId, quantity, null);
    }

    /** Igual que {@link #deduct(UUID, UUID, BigDecimal)}, registrando una referencia (por ejemplo, el despacho). */
    @Transactional
    public StockLevel deduct(UUID locationId, UUID materialId, BigDecimal quantity, String reference) {
        return operations.deduct(locationId, materialId, quantity, reference);
    }

    /** Suma stock; crea el registro del material en el almacén si no existía. */
    @Transactional
    public StockLevel add(UUID locationId, UUID materialId, BigDecimal quantity) {
        return operations.add(locationId, materialId, quantity, null);
    }

    /** Suma stock en un almacén u obra (por ejemplo, lo recibido en obra), registrando una referencia. */
    @Transactional
    public StockLevel add(UUID locationId, UUID materialId, BigDecimal quantity, String reference) {
        return operations.add(locationId, materialId, quantity, reference);
    }

    /**
     * RF40, QAS02: reserva lo solicitado por una línea de pedido aprobada (disponible a reservado), con
     * bloqueo optimista y sin dejar el disponible negativo.
     *
     * @throws InsufficientStockException          si no alcanza; no modifica nada
     * @throws ConcurrentStockModificationException si se agotaron los reintentos
     */
    @Transactional
    public StockLevel reserve(UUID locationId, UUID materialId, BigDecimal quantity, UUID orderId, UUID orderLineId) {
        return operations.reserve(locationId, materialId, quantity, orderId, orderLineId);
    }

    /**
     * RF41: consume lo reservado para la línea de pedido cuando parte el despacho (reservado a salida).
     *
     * @throws pe.buildshield.core.shared.error.ConflictException {@code RESERVATION_NOT_AVAILABLE} si no hay
     *         reserva suficiente para esa línea
     */
    @Transactional
    public StockLevel consumeReservation(UUID locationId, UUID materialId, BigDecimal quantity, UUID orderLineId,
            String reference) {
        return operations.consumeReservation(locationId, materialId, quantity, orderLineId, reference);
    }

    /** Saldo después de la operación. */
    public record StockLevel(UUID locationId, UUID materialId, BigDecimal availableQty, BigDecimal reservedQty) {
    }
}
