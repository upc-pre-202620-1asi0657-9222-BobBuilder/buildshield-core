package pe.buildshield.core.organization.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tolerancia de merma de un material: porcentaje entre 0 y 100, ambos incluidos, con hasta 2
 * decimales. Al cotejar una recepción, una merma por encima de este porcentaje es una discrepancia.
 */
public record WasteTolerance(BigDecimal percent) {

    public static final String INVALID_WASTE_TOLERANCE = "INVALID_WASTE_TOLERANCE";
    private static final BigDecimal MAX = BigDecimal.valueOf(100);

    public WasteTolerance {
        if (percent == null || percent.signum() < 0 || percent.compareTo(MAX) > 0
                || percent.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(INVALID_WASTE_TOLERANCE,
                    "La tolerancia de merma debe ser un porcentaje entre 0 y 100, con hasta 2 decimales",
                    List.of(new ErrorDetail("wasteTolerancePercent", "entre 0 y 100, máximo 2 decimales")));
        }
        percent = percent.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** ¿La merma observada (en %) supera la tolerancia? */
    public boolean isExceededBy(BigDecimal observedWastePercent) {
        return observedWastePercent.compareTo(percent) > 0;
    }
}
