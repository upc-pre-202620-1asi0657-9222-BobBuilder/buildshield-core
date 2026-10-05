package pe.buildshield.core.iam.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Correo electrónico normalizado (sin espacios y en minúsculas). Identifica al usuario al iniciar sesión. */
public record EmailAddress(String value) {

    static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new ValidationException("INVALID_EMAIL", "El correo no es válido",
                    List.of(new ErrorDetail("email", "formato de correo inválido")));
        }
        value = normalized;
    }

    /** Versión para logs: {@code a***@dominio.pe}. */
    public String masked() {
        int at = value.indexOf('@');
        return value.charAt(0) + "***" + value.substring(at);
    }
}
