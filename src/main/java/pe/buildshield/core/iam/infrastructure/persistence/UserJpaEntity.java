package pe.buildshield.core.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.buildshield.commons.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;

/**
 * Modelo de persistencia de {@link User}. Hereda {@code organization_id} (filtro multiempresa),
 * auditoría y {@code version}.
 */
@Entity
@Table(schema = "iam", name = "users")
public class UserJpaEntity extends AuditableAbstractAggregateRoot<UserJpaEntity> {

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected UserJpaEntity() {
    }

    UserJpaEntity(User user) {
        apply(user);
    }

    void apply(User user) {
        email = user.email().value();
        fullName = user.fullName();
        role = user.role();
        passwordHash = user.passwordHash();
        active = user.active();
    }

    User toDomain() {
        return User.restore(getId(), getOrganizationId(), new EmailAddress(email), fullName, role, passwordHash,
                active, getVersion());
    }
}
