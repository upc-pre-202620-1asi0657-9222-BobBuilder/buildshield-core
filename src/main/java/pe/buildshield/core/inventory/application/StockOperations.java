package pe.buildshield.core.inventory.application;

import org.springframework.stereotype.Service;
import pe.buildshield.core.inventory.ConcurrentStockModificationException;
import pe.buildshield.core.inventory.InsufficientStockException;
import pe.buildshield.core.inventory.StockService.StockLevel;
import pe.buildshield.core.inventory.domain.model.Quantity;
import pe.buildshield.core.inventory.domain.model.StockItem;
import pe.buildshield.core.inventory.domain.model.StockMovement;
import pe.buildshield.core.inventory.domain.model.StockRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Entradas y descuentos de stock (QAS02: el inventario nunca queda negativo).
 *
 * <p>El descuento lee el ítem y ejecuta un UPDATE condicionado por la versión leída y por la cantidad
 * disponible. Si no actualiza nada, vuelve a leer: si ya no alcanza, falla con
 * {@link InsufficientStockException}; si alcanza, fue un conflicto de versión y reintenta, hasta
 * {@value #MAX_RETRIES} veces. El movimiento se registra solo si el UPDATE tuvo efecto.
 */
@Service
public class StockOperations {

    public static final int MAX_RETRIES = 3;

    private final StockRepository stock;
    private final Clock clock;

    public StockOperations(StockRepository stock, Clock clock) {
        this.stock = stock;
        this.clock = clock;
    }

    public StockLevel deduct(UUID locationId, UUID materialId, BigDecimal requested, String reference) {
        Quantity quantity = new Quantity(requested);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Optional<StockItem> current = stock.find(locationId, materialId);
            StockItem item = current.orElseThrow(() ->
                    new InsufficientStockException(locationId, materialId, BigDecimal.ZERO, quantity.value()));
            if (!item.hasAvailable(quantity)) {
                throw new InsufficientStockException(locationId, materialId, item.availableQty(), quantity.value());
            }
            if (stock.tryDeduct(item.id(), item.version(), quantity)) {
                BigDecimal balance = item.availableAfterDeducting(quantity);
                stock.record(new StockMovement(item.id(), StockMovement.Type.DEDUCTION, quantity.value(), balance,
                        reference, clock.instant()));
                return new StockLevel(locationId, materialId, balance, item.reservedQty());
            }
        }
        throw new ConcurrentStockModificationException(locationId, materialId, MAX_RETRIES + 1);
    }

    public StockLevel add(UUID locationId, UUID materialId, BigDecimal added, String reference) {
        Quantity quantity = new Quantity(added);
        StockItem item = stock.add(locationId, materialId, quantity);
        stock.record(new StockMovement(item.id(), StockMovement.Type.ENTRY, quantity.value(), item.availableQty(),
                reference, clock.instant()));
        return new StockLevel(locationId, materialId, item.availableQty(), item.reservedQty());
    }
}
