package pe.buildshield.core.inventory.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.stereotype.Service;
import pe.buildshield.core.inventory.ConcurrentStockModificationException;
import pe.buildshield.core.inventory.InsufficientStockException;
import pe.buildshield.core.inventory.StockService.StockLevel;
import pe.buildshield.core.inventory.domain.model.Quantity;
import pe.buildshield.core.inventory.domain.model.StockItem;
import pe.buildshield.core.inventory.domain.model.StockMovement;
import pe.buildshield.core.inventory.domain.model.StockRepository;
import pe.buildshield.core.inventory.domain.model.StockReservation;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ErrorDetail;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entradas, descuentos, reservas y consumos de stock (QAS02: el inventario nunca queda negativo).
 *
 * <p>El descuento lee el ítem y ejecuta un UPDATE condicionado por la versión leída y por la cantidad
 * disponible. Si no actualiza nada, vuelve a leer: si ya no alcanza, falla con
 * {@link InsufficientStockException}; si alcanza, fue un conflicto de versión y reintenta, hasta
 * {@value #MAX_RETRIES} veces. El movimiento se registra solo si el UPDATE tuvo efecto.
 *
 * <p>La reserva (al aprobar un pedido) y su consumo (al despachar) siguen el mismo esquema: UPDATE
 * condicionado por versión y por la cantidad disponible o reservada, con los mismos reintentos.
 */
@Service
public class StockOperations {

    public static final int MAX_RETRIES = 3;
    public static final String RESERVATION_NOT_AVAILABLE = "RESERVATION_NOT_AVAILABLE";

    private final AuditTrail audit;
    private final StockRepository stock;
    private final Clock clock;

    public StockOperations(StockRepository stock, Clock clock, AuditTrail audit) {
        this.stock = stock;
        this.clock = clock;
        this.audit = audit;
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
                audit.record("STOCK_DEDUCTED", "STOCK_ITEM", item.id(), Map.of(
                        "warehouseId", locationId, "materialId", materialId, "quantity", quantity.value(),
                        "balanceAfter", balance, "reference", reference == null ? "" : reference));
                return new StockLevel(locationId, materialId, balance, item.reservedQty());
            }
        }
        throw new ConcurrentStockModificationException(locationId, materialId, MAX_RETRIES + 1);
    }

    /**
     * RF40: reserva {@code requested} para una línea de pedido aprobada; pasa de disponible a reservado.
     *
     * @throws InsufficientStockException si no hay disponible suficiente; no modifica nada
     */
    public StockLevel reserve(UUID locationId, UUID materialId, BigDecimal requested, UUID orderId, UUID orderLineId) {
        Quantity quantity = new Quantity(requested);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            StockItem item = stock.find(locationId, materialId).orElseThrow(() ->
                    new InsufficientStockException(locationId, materialId, BigDecimal.ZERO, quantity.value()));
            if (!item.hasAvailable(quantity)) {
                throw new InsufficientStockException(locationId, materialId, item.availableQty(), quantity.value());
            }
            if (stock.tryReserve(item.id(), item.version(), quantity)) {
                BigDecimal available = item.availableAfterDeducting(quantity);
                BigDecimal reserved = item.reservedAfterReserving(quantity);
                stock.saveReservation(StockReservation.reserve(item.id(), orderId, orderLineId, quantity, clock.instant()));
                stock.record(new StockMovement(item.id(), StockMovement.Type.RESERVE, quantity.value(), available,
                        "Pedido " + orderId, clock.instant()));
                audit.record("STOCK_RESERVED", "STOCK_ITEM", item.id(), Map.of(
                        "warehouseId", locationId, "materialId", materialId, "quantity", quantity.value(),
                        "availableAfter", available, "reservedAfter", reserved,
                        "orderId", orderId, "orderLineId", orderLineId));
                return new StockLevel(locationId, materialId, available, reserved);
            }
        }
        throw new ConcurrentStockModificationException(locationId, materialId, MAX_RETRIES + 1);
    }

    /**
     * RF41: consume {@code requested} de la reserva de la línea de pedido; sale de lo reservado del almacén.
     *
     * @throws ConflictException {@code RESERVATION_NOT_AVAILABLE} si la línea no tiene reserva suficiente
     */
    public StockLevel consumeReservation(UUID locationId, UUID materialId, BigDecimal requested, UUID orderLineId,
            String reference) {
        Quantity quantity = new Quantity(requested);
        StockReservation reservation = stock.findReservation(orderLineId)
                .filter(found -> found.canConsume(quantity))
                .orElseThrow(() -> reservationNotAvailable(orderLineId, quantity));
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            StockItem item = stock.find(locationId, materialId)
                    .filter(found -> found.id().equals(reservation.stockItemId()))
                    .orElseThrow(() -> reservationNotAvailable(orderLineId, quantity));
            if (!item.hasReserved(quantity)) {
                throw reservationNotAvailable(orderLineId, quantity);
            }
            if (stock.tryConsumeReserved(item.id(), item.version(), quantity)) {
                if (!stock.tryConsumeReservation(orderLineId, quantity)) {
                    throw reservationNotAvailable(orderLineId, quantity);
                }
                BigDecimal reserved = item.reservedAfterConsuming(quantity);
                stock.record(new StockMovement(item.id(), StockMovement.Type.DISPATCH, quantity.value(),
                        item.availableQty(), reference, clock.instant()));
                audit.record("STOCK_DISPATCHED", "STOCK_ITEM", item.id(), Map.of(
                        "warehouseId", locationId, "materialId", materialId, "quantity", quantity.value(),
                        "reservedAfter", reserved, "orderLineId", orderLineId,
                        "reference", reference == null ? "" : reference));
                return new StockLevel(locationId, materialId, item.availableQty(), reserved);
            }
        }
        throw new ConcurrentStockModificationException(locationId, materialId, MAX_RETRIES + 1);
    }

    public StockLevel add(UUID locationId, UUID materialId, BigDecimal added, String reference) {
        Quantity quantity = new Quantity(added);
        StockItem item = stock.add(locationId, materialId, quantity);
        stock.record(new StockMovement(item.id(), StockMovement.Type.ENTRY, quantity.value(), item.availableQty(),
                reference, clock.instant()));
        audit.record("STOCK_ADDED", "STOCK_ITEM", item.id(), Map.of(
                "warehouseId", locationId, "materialId", materialId, "quantity", quantity.value(),
                "balanceAfter", item.availableQty(), "reference", reference == null ? "" : reference));
        return new StockLevel(locationId, materialId, item.availableQty(), item.reservedQty());
    }

    private static ConflictException reservationNotAvailable(UUID orderLineId, Quantity quantity) {
        return new ConflictException(RESERVATION_NOT_AVAILABLE,
                "La línea de pedido no tiene reservado lo que se intenta despachar",
                List.of(new ErrorDetail("orderLineId", orderLineId.toString()),
                        new ErrorDetail("quantity", quantity.value().toPlainString())));
    }
}
