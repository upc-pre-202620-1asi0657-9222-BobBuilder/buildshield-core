package pe.buildshield.core.organization.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Empresa cliente de BuildShield. Su identificador es el {@code organization_id} de todos sus datos. */
public class Organization {

    static final int MAX_LEGAL_NAME = 200;

    private final UUID id;
    private final Ruc ruc;
    private final String legalName;

    private Organization(UUID id, Ruc ruc, String legalName) {
        this.id = Objects.requireNonNull(id, "id");
        this.ruc = Objects.requireNonNull(ruc, "ruc");
        this.legalName = legalName;
    }

    public static Organization register(UUID id, Ruc ruc, String legalName) {
        if (legalName == null || legalName.isBlank() || legalName.trim().length() > MAX_LEGAL_NAME) {
            throw new ValidationException("INVALID_LEGAL_NAME", "La razón social es obligatoria (máximo 200 caracteres)",
                    List.of(new ErrorDetail("legalName", "obligatoria, máximo 200 caracteres")));
        }
        return new Organization(id, ruc, legalName.trim());
    }

    public static Organization restore(UUID id, Ruc ruc, String legalName) {
        return new Organization(id, ruc, legalName);
    }

    public UUID id() {
        return id;
    }

    public Ruc ruc() {
        return ruc;
    }

    public String legalName() {
        return legalName;
    }
}
