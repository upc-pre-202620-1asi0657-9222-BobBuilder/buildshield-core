package pe.buildshield.core.dispatch.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Material de una línea de pedido que sale en el despacho, con su cantidad (mayor que cero, hasta 3 decimales). */
public record DispatchLine(UUID id, UUID orderLineId, UUID materialId, BigDecimal quantity) {

    public static final int SCALE = 3;

    public DispatchLine {
        Objects.requireNonNull(orderLineId, "orderLineId");
        Objects.requireNonNull(materialId, "materialId");
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException("INVALID_QUANTITY", "La cantidad debe ser mayor que cero, con hasta 3 decimales",
                    List.of(new ErrorDetail("quantity", "mayor que cero, máximo 3 decimales")));
        }
        quantity = quantity.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** Línea nueva; el identificador lo asigna la persistencia. */
    public static DispatchLine of(UUID orderLineId, UUID materialId, BigDecimal quantity) {
        return new DispatchLine(null, orderLineId, materialId, quantity);
    }
}
