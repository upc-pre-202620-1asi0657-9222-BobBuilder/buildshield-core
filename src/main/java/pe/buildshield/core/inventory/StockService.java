package pe.buildshield.core.inventory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.inventory.application.StockOperations;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fachada pública del módulo inventory: única forma en que otros módulos (por ejemplo, despachos)
 * mueven stock. Se une a la transacción de quien llama, así el descuento y el resto del despacho se
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

    /** Saldo después de la operación. */
    public record StockLevel(UUID locationId, UUID materialId, BigDecimal availableQty, BigDecimal reservedQty) {
    }
}
