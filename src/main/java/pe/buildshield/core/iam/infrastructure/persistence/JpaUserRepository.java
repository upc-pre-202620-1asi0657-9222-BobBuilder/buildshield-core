package pe.buildshield.core.iam.infrastructure.persistence;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return jpa.findByEmail(email.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmailInAnyOrganization(EmailAddress email) {
        return jpa.existsByEmailInAnyOrganization(email.value());
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public List<User> findAll() {
        return jpa.findAllByOrderByEmail().stream().map(UserJpaEntity::toDomain).toList();
    }

    @Override
    public User save(User user) {
        if (user.id() == null) {
            // Hibernate asigna organization_id desde el TenantContext; debe ser la del usuario.
            UUID contextOrganization = TenantContext.require().organizationId();
            if (!contextOrganization.equals(user.organizationId())) {
                throw new IllegalStateException("El usuario pertenece a otra organización que la del contexto");
            }
            return jpa.saveAndFlush(new UserJpaEntity(user)).toDomain();
        }
        UserJpaEntity entity = jpa.findById(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "El usuario no existe"));
        if (!Objects.equals(entity.getVersion(), user.version())) {
            throw new ObjectOptimisticLockingFailureException(UserJpaEntity.class, user.id());
        }
        entity.apply(user);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
