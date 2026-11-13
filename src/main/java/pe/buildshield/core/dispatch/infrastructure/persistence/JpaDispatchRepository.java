package pe.buildshield.core.dispatch.infrastructure.persistence;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.dispatch.domain.model.Dispatch;
import pe.buildshield.core.dispatch.domain.model.DispatchRepository;
import pe.buildshield.core.shared.error.ResourceNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaDispatchRepository implements DispatchRepository {

    private final SpringDataDispatchRepository jpa;

    JpaDispatchRepository(SpringDataDispatchRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Dispatch> findById(UUID id) {
        return jpa.findById(id).map(DispatchJpaEntity::toDomain);
    }

    @Override
    public List<Dispatch> findAll() {
        return jpa.findAllByOrderByPreparedAtDesc().stream().map(DispatchJpaEntity::toDomain).toList();
    }

    @Override
    public List<Dispatch> findBySites(Collection<UUID> siteIds) {
        if (siteIds.isEmpty()) {
            return List.of();
        }
        return jpa.findBySites(siteIds).stream().map(DispatchJpaEntity::toDomain).toList();
    }

    @Override
    public Dispatch save(Dispatch dispatch) {
        if (dispatch.id() == null) {
            return jpa.saveAndFlush(new DispatchJpaEntity(dispatch)).toDomain();
        }
        DispatchJpaEntity entity = jpa.findById(dispatch.id())
                .orElseThrow(() -> new ResourceNotFoundException("DISPATCH_NOT_FOUND", "El despacho no existe"));
        if (!Objects.equals(entity.getVersion(), dispatch.version())) {
            throw new ObjectOptimisticLockingFailureException(DispatchJpaEntity.class, dispatch.id());
        }
        entity.apply(dispatch);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
