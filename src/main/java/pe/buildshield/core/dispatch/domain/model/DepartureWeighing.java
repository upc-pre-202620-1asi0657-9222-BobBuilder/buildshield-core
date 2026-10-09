package pe.buildshield.core.dispatch.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pesaje de salida del despacho (US27): peso bruto y tara en kg, con hasta 3 decimales. El neto es
 * bruto menos tara y debe ser mayor que cero. La foto del ticket es una referencia a la evidencia
 * guardada (opcional).
 */
public record DepartureWeighing(BigDecimal grossKg, BigDecimal tareKg, BigDecimal netKg, String ticketPhotoUrl,
        Instant weighedAt, UUID weighedBy) {

    public static final String INVALID_WEIGHING = "INVALID_WEIGHING";
    public static final int SCALE = 3;

    public DepartureWeighing {
        Objects.requireNonNull(weighedAt, "weighedAt");
        Objects.requireNonNull(weighedBy, "weighedBy");
        grossKg = weight(grossKg, "grossKg", false);
        tareKg = weight(tareKg, "tareKg", true);
        if (netKg == null || netKg.compareTo(grossKg.subtract(tareKg)) != 0) {
            throw new IllegalStateException("El neto es bruto menos tara");
        }
        netKg = netKg.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (netKg.signum() <= 0) {
            throw new ValidationException(INVALID_WEIGHING, "La tara debe ser menor que el peso bruto",
                    List.of(new ErrorDetail("tareKg", "menor que grossKg")));
        }
        ticketPhotoUrl = ticketPhotoUrl == null || ticketPhotoUrl.isBlank() ? null : ticketPhotoUrl.trim();
    }

    /** Calcula el neto a partir del bruto y la tara. */
    public static DepartureWeighing of(BigDecimal grossKg, BigDecimal tareKg, String ticketPhotoUrl, Instant weighedAt,
            UUID weighedBy) {
        BigDecimal gross = weight(grossKg, "grossKg", false);
        BigDecimal tare = weight(tareKg, "tareKg", true);
        return new DepartureWeighing(gross, tare, gross.subtract(tare), ticketPhotoUrl, weighedAt, weighedBy);
    }

    private static BigDecimal weight(BigDecimal value, String field, boolean zeroAllowed) {
        if (value == null || value.signum() < 0 || (!zeroAllowed && value.signum() == 0)
                || value.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException(INVALID_WEIGHING, "El peso debe ser " + (zeroAllowed ? "cero o más" : "mayor que cero")
                    + ", en kg con hasta 3 decimales", List.of(new ErrorDetail(field, "kg, máximo 3 decimales")));
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
