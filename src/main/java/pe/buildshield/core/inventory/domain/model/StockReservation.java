package pe.buildshield.core.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reserva del stock de una línea de pedido aprobada (RF40). Lo reservado sale del disponible del
 * almacén y se consume, total o parcialmente, cuando parte cada despacho (RF41).
 */
public record StockReservation(UUID id, UUID stockItemId, UUID orderId, UUID orderLineId, BigDecimal quantity,
        BigDecimal consumedQty, Status status, Instant reservedAt) {

    public enum Status { RESERVED, CONSUMED, RELEASED }

    public StockReservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stockItemId, "stockItemId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderLineId, "orderLineId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(consumedQty, "consumedQty");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reservedAt, "reservedAt");
        if (quantity.signum() <= 0 || consumedQty.signum() < 0 || consumedQty.compareTo(quantity) > 0) {
            throw new IllegalStateException("Una reserva no consume más de lo reservado");
        }
    }

    /** Nueva reserva de toda la cantidad, sin consumir. */
    public static StockReservation reserve(UUID stockItemId, UUID orderId, UUID orderLineId, Quantity quantity,
            Instant now) {
        return new StockReservation(UUID.randomUUID(), stockItemId, orderId, orderLineId, quantity.value(),
                BigDecimal.ZERO.setScale(Quantity.SCALE), Status.RESERVED, now);
    }

    public BigDecimal remaining() {
        return quantity.subtract(consumedQty);
    }

    /** ¿Queda reservado al menos {@code amount} para consumir? */
    public boolean canConsume(Quantity amount) {
        return status == Status.RESERVED && remaining().compareTo(amount.value()) >= 0;
    }
}
