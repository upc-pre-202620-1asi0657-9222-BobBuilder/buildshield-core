package pe.buildshield.core.iam.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario de una organización. El identificador lo asigna la persistencia al guardarlo por primera
 * vez; hasta entonces {@link #id()} es {@code null}.
 */
public class User {

    static final int MAX_FULL_NAME = 150;

    private final UUID id;
    private final UUID organizationId;
    private final EmailAddress email;
    private final String fullName;
    private final Role role;
    private String passwordHash;
    private final boolean active;
    private final Long version;

    private User(UUID id, UUID organizationId, EmailAddress email, String fullName, Role role, String passwordHash,
            boolean active, Long version) {
        this.id = id;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.email = Objects.requireNonNull(email, "email");
        this.fullName = fullName;
        this.role = Objects.requireNonNull(role, "role");
        this.passwordHash = requireHash(passwordHash);
        this.active = active;
        this.version = version;
    }

    public static User register(UUID organizationId, EmailAddress email, String fullName, Role role, String passwordHash) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > MAX_FULL_NAME) {
            throw new ValidationException("INVALID_FULL_NAME", "El nombre es obligatorio (máximo 150 caracteres)",
                    List.of(new ErrorDetail("fullName", "obligatorio, máximo 150 caracteres")));
        }
        return new User(null, organizationId, email, fullName.trim(), role, passwordHash, true, null);
    }

    public static User restore(UUID id, UUID organizationId, EmailAddress email, String fullName, Role role,
            String passwordHash, boolean active, Long version) {
        return new User(Objects.requireNonNull(id, "id"), organizationId, email, fullName, role, passwordHash, active, version);
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = requireHash(newPasswordHash);
    }

    public boolean canSignIn() {
        return active;
    }

    private static String requireHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Se requiere el hash de la contraseña");
        }
        return hash;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public EmailAddress email() {
        return email;
    }

    public String fullName() {
        return fullName;
    }

    public Role role() {
        return role;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean active() {
        return active;
    }

    public Long version() {
        return version;
    }
}
