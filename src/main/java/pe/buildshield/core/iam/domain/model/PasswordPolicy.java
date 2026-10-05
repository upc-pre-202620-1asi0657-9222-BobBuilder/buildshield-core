package pe.buildshield.core.iam.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.List;

/**
 * Política de contraseñas: entre 8 y 72 caracteres (72 es el límite de bcrypt), con al menos una
 * letra y un dígito.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password, String field) {
        boolean valid = password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!valid) {
            throw new ValidationException("WEAK_PASSWORD",
                    "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número",
                    List.of(new ErrorDetail(field, "contraseña insegura")));
        }
    }
}
