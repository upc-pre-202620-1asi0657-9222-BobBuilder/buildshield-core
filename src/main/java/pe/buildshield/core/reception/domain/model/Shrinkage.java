package pe.buildshield.core.reception.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Merma de un material (QAS01): lo que faltó respecto de lo despachado, en porcentaje con 2 decimales.
 * {@code merma % = (despachado - recibido) / despachado × 100}. Está dentro de la tolerancia si no la
 * supera.
 */
public final class Shrinkage {

    public static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Shrinkage() {
    }

    public static BigDecimal percent(BigDecimal dispatched, BigDecimal received) {
        if (dispatched == null || dispatched.signum() <= 0) {
            throw new IllegalArgumentException("Lo despachado debe ser mayor que cero");
        }
        return dispatched.subtract(received).multiply(HUNDRED).divide(dispatched, SCALE, RoundingMode.HALF_UP);
    }

    public static boolean withinTolerance(BigDecimal shrinkagePercent, BigDecimal tolerancePercent) {
        return shrinkagePercent.compareTo(tolerancePercent) <= 0;
    }
}
