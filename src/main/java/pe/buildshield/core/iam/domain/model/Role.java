package pe.buildshield.core.iam.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.util.Arrays;
import java.util.List;

/** Roles de un usuario dentro de su organización. */
public enum Role {

    ADMINISTRATOR("administrador"),
    WAREHOUSE_MANAGER("encargado de almacén"),
    SITE_MANAGER("encargado de obra");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Acepta el nombre del rol ({@code WAREHOUSE_MANAGER}). */
    public static Role parse(String value) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(value == null ? "" : value.trim()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("INVALID_ROLE",
                        "El rol debe ser ADMINISTRATOR, WAREHOUSE_MANAGER o SITE_MANAGER",
                        List.of(new ErrorDetail("role", "rol desconocido: " + value))));
    }
}
