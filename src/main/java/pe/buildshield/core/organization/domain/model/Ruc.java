package pe.buildshield.core.organization.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.List;
import java.util.regex.Pattern;

/** Registro Único de Contribuyentes: 11 dígitos. */
public record Ruc(String value) {

    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\\d{11}");

    public Ruc {
        if (value == null || !ELEVEN_DIGITS.matcher(value.trim()).matches()) {
            throw new ValidationException("INVALID_RUC", "El RUC debe tener 11 dígitos",
                    List.of(new ErrorDetail("ruc", "debe tener 11 dígitos")));
        }
        value = value.trim();
    }
}
