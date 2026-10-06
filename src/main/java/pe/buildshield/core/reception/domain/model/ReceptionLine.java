package pe.buildshield.core.reception.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Material recibido de una línea del despacho. Guarda lo despachado (copia de la línea del despacho) y lo
 * recibido, que se registra de a poco; al registrarlo calcula la merma.
 *
 * <p>Invariantes: lo recibido es cero o más, con hasta 3 decimales, y no supera lo despachado.
 */
public class ReceptionLine {

    public static final int SCALE = 3;
    public static final String RECEIVED_EXCEEDS_DISPATCHED = "RECEIVED_EXCEEDS_DISPATCHED";

    private final UUID id;
    private final UUID dispatchLineId;
    private final UUID orderLineId;
    private final UUID materialId;
    private final BigDecimal dispatchedQty;
    private BigDecimal receivedQty;
    private BigDecimal shrinkagePercent;

    private ReceptionLine(UUID id, UUID dispatchLineId, UUID orderLineId, UUID materialId, BigDecimal dispatchedQty,
            BigDecimal receivedQty, BigDecimal shrinkagePercent) {
        this.id = id;
        this.dispatchLineId = Objects.requireNonNull(dispatchLineId, "dispatchLineId");
        this.orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        this.materialId = Objects.requireNonNull(materialId, "materialId");
        if (dispatchedQty == null || dispatchedQty.signum() <= 0) {
            throw new IllegalStateException("Lo despachado debe ser mayor que cero");
        }
        this.dispatchedQty = dispatchedQty.setScale(SCALE, RoundingMode.HALF_UP);
        this.receivedQty = receivedQty == null ? null : receivedQty.setScale(SCALE, RoundingMode.HALF_UP);
        this.shrinkagePercent = shrinkagePercent;
    }

    /** Línea por recibir, a partir de una línea del despacho. */
    public static ReceptionLine expect(UUID dispatchLineId, UUID orderLineId, UUID materialId, BigDecimal dispatchedQty) {
        return new ReceptionLine(null, dispatchLineId, orderLineId, materialId, dispatchedQty, null, null);
    }

    public static ReceptionLine restore(UUID id, UUID dispatchLineId, UUID orderLineId, UUID materialId,
            BigDecimal dispatchedQty, BigDecimal receivedQty, BigDecimal shrinkagePercent) {
        return new ReceptionLine(Objects.requireNonNull(id, "id"), dispatchLineId, orderLineId, materialId,
                dispatchedQty, receivedQty, shrinkagePercent);
    }

    /** Registra (o corrige) lo recibido y recalcula la merma. */
    void recordReceived(BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0 || quantity.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException("INVALID_QUANTITY", "Lo recibido debe ser cero o más, con hasta 3 decimales",
                    List.of(new ErrorDetail("receivedQty", "cero o más, máximo 3 decimales")));
        }
        BigDecimal received = quantity.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (received.compareTo(dispatchedQty) > 0) {
            throw new ValidationException(RECEIVED_EXCEEDS_DISPATCHED, "Lo recibido (" + received.toPlainString()
                    + ") supera lo despachado (" + dispatchedQty.toPlainString() + ")",
                    List.of(new ErrorDetail("receivedQty", "máximo " + dispatchedQty.toPlainString())));
        }
        receivedQty = received;
        shrinkagePercent = Shrinkage.percent(dispatchedQty, received);
    }

    public boolean isRecorded() {
        return receivedQty != null;
    }

    /** Lo que faltó: despachado - recibido; {@code null} si todavía no se registró lo recibido. */
    public BigDecimal difference() {
        return receivedQty == null ? null : dispatchedQty.subtract(receivedQty);
    }

    /** {@code null} hasta que la línea se guarda. */
    public UUID id() {
        return id;
    }

    public UUID dispatchLineId() {
        return dispatchLineId;
    }

    public UUID orderLineId() {
        return orderLineId;
    }

    public UUID materialId() {
        return materialId;
    }

    public BigDecimal dispatchedQty() {
        return dispatchedQty;
    }

    public BigDecimal receivedQty() {
        return receivedQty;
    }

    public BigDecimal shrinkagePercent() {
        return shrinkagePercent;
    }
}
