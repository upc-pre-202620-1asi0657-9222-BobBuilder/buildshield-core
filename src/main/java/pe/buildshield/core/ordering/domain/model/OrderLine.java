package pe.buildshield.core.ordering.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Línea de pedido: un material con lo solicitado, despachado, cancelado y recibido por separado.
 * Lo pendiente no se guarda: {@code pending = requested - dispatched - cancelled}.
 *
 * <p>Invariantes: lo solicitado es mayor que cero; despachado, cancelado y recibido nunca son
 * negativos; despachado + cancelado no supera lo solicitado; lo recibido no supera lo despachado.
 */
public class OrderLine {

    public static final int SCALE = 3;

    private final UUID materialId;
    private final String sku;
    private final String unit;
    private final BigDecimal requested;
    private BigDecimal dispatched;
    private BigDecimal cancelled;
    private BigDecimal received;

    private OrderLine(UUID materialId, String sku, String unit, BigDecimal requested, BigDecimal dispatched,
            BigDecimal cancelled, BigDecimal received) {
        this.materialId = Objects.requireNonNull(materialId, "materialId");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.requested = positive(requested, "requested");
        this.dispatched = notNegative(dispatched, "dispatched");
        this.cancelled = notNegative(cancelled, "cancelled");
        this.received = notNegative(received, "received");
        if (this.dispatched.add(this.cancelled).compareTo(this.requested) > 0) {
            throw new IllegalStateException("Despachado + cancelado supera lo solicitado");
        }
        if (this.received.compareTo(this.dispatched) > 0) {
            throw new IllegalStateException("Lo recibido supera lo despachado");
        }
    }

    public static OrderLine request(UUID materialId, String sku, String unit, BigDecimal requested) {
        return new OrderLine(materialId, sku, unit, requested, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static OrderLine restore(UUID materialId, String sku, String unit, BigDecimal requested,
            BigDecimal dispatched, BigDecimal cancelled, BigDecimal received) {
        return new OrderLine(materialId, sku, unit, requested, dispatched, cancelled, received);
    }

    public BigDecimal pending() {
        return requested.subtract(dispatched).subtract(cancelled);
    }

    public boolean isComplete() {
        return pending().signum() == 0;
    }

    /** Suma lo despachado; no puede superar lo pendiente. */
    public void registerDispatch(BigDecimal quantity) {
        BigDecimal amount = positive(quantity, "quantity");
        if (amount.compareTo(pending()) > 0) {
            throw new ValidationException("DISPATCH_EXCEEDS_PENDING",
                    "Se intenta despachar " + amount.toPlainString() + " y solo quedan " + pending().toPlainString(),
                    List.of(new ErrorDetail("quantity", "máximo " + pending().toPlainString())));
        }
        dispatched = dispatched.add(amount);
    }

    /** Suma lo recibido en obra; no puede superar lo despachado. */
    public void registerReceived(BigDecimal quantity) {
        BigDecimal amount = positive(quantity, "quantity");
        if (received.add(amount).compareTo(dispatched) > 0) {
            throw new ValidationException("RECEIVED_EXCEEDS_DISPATCHED", "Lo recibido supera lo despachado");
        }
        received = received.add(amount);
    }

    /** Cancela lo que queda pendiente; lo despachado se conserva. */
    public void cancelRemaining() {
        cancelled = cancelled.add(pending());
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException("INVALID_QUANTITY", "La cantidad debe ser mayor que cero, con hasta 3 decimales",
                    List.of(new ErrorDetail(field, "mayor que cero, máximo 3 decimales")));
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal notNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalStateException("La cantidad " + field + " no puede ser negativa");
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public UUID materialId() {
        return materialId;
    }

    public String sku() {
        return sku;
    }

    public String unit() {
        return unit;
    }

    public BigDecimal requested() {
        return requested;
    }

    public BigDecimal dispatched() {
        return dispatched;
    }

    public BigDecimal cancelled() {
        return cancelled;
    }

    public BigDecimal received() {
        return received;
    }
}
