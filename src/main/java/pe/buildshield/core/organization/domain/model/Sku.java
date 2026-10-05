package pe.buildshield.core.organization.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código del material, único dentro de la organización. Se normaliza a mayúsculas: "cem-001" y
 * "CEM-001" son el mismo SKU.
 */
public record Sku(String value) {

    static final int MAX_LENGTH = 40;
    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._/-]*");

    public Sku {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new ValidationException("INVALID_SKU",
                    "El SKU debe tener hasta 40 caracteres: letras, números, '.', '_', '/' o '-', sin espacios",
                    List.of(new ErrorDetail("sku", "formato inválido")));
        }
        value = normalized;
    }
}
