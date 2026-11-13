package pe.buildshield.core.dispatch.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Transportista del despacho (US30): nombre, documento (DNI, RUC o carné, de 8 a 20 caracteres) y
 * placa del vehículo (por ejemplo, ABC-123). La placa se guarda en mayúsculas.
 */
public record Carrier(String name, String document, String plate) {

    public static final String INVALID_CARRIER = "INVALID_CARRIER";
    static final int MAX_NAME = 150;
    private static final Pattern DOCUMENT = Pattern.compile("[0-9A-Za-z-]{8,20}");
    private static final Pattern PLATE = Pattern.compile("[A-Z0-9]{2,4}-?[A-Z0-9]{3,4}");

    public Carrier {
        name = name == null ? "" : name.trim();
        document = document == null ? "" : document.trim();
        plate = plate == null ? "" : plate.trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw invalid("carrierName", "obligatorio, máximo 150 caracteres");
        }
        if (!DOCUMENT.matcher(document).matches()) {
            throw invalid("carrierDocument", "de 8 a 20 letras, números o guiones (DNI, RUC o carné)");
        }
        if (!PLATE.matcher(plate).matches()) {
            throw invalid("plate", "placa válida, por ejemplo ABC-123");
        }
    }

    private static ValidationException invalid(String field, String detail) {
        return new ValidationException(INVALID_CARRIER, "Los datos del transportista no son válidos",
                List.of(new ErrorDetail(field, detail)));
    }
}
