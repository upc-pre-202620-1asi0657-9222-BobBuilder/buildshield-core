package pe.buildshield.core.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registro inmutable de una entrada o salida de stock, con el saldo que dejó. */
public record StockMovement(UUID stockItemId, Type type, BigDecimal quantity, BigDecimal balanceAfter, String reference,
        Instant occurredAt) {

    public enum Type { ENTRY, DEDUCTION }

    public StockMovement {
        Objects.requireNonNull(stockItemId, "stockItemId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (balanceAfter.signum() < 0) {
            throw new IllegalStateException("Un movimiento no puede dejar stock negativo");
        }
    }
}
