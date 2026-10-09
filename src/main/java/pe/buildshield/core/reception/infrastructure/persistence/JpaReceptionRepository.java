package pe.buildshield.core.reception.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.reception.domain.model.Reception;
import pe.buildshield.core.reception.domain.model.ReceptionRepository;
import pe.buildshield.core.shared.error.ResourceNotFoundException;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaReceptionRepository implements ReceptionRepository {

    private final SpringDataReceptionRepository jpa;
    private final EntityManager entityManager;

    JpaReceptionRepository(SpringDataReceptionRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Reception> findById(UUID id) {
        return jpa.findWithLines(id).map(ReceptionJpaEntity::toDomain);
    }

    @Override
    public Optional<UUID> findIdByDispatchId(UUID dispatchId) {
        return jpa.findIdByDispatchId(dispatchId);
    }

    @Override
    public Reception save(Reception reception) {
        if (reception.id() == null) {
            return jpa.saveAndFlush(new ReceptionJpaEntity(reception)).toDomain();
        }
        ReceptionJpaEntity entity = jpa.findWithLines(reception.id())
                .orElseThrow(() -> new ResourceNotFoundException("RECEPTION_NOT_FOUND", "La recepción no existe"));
        if (!Objects.equals(entity.getVersion(), reception.version())) {
            throw new ObjectOptimisticLockingFailureException(ReceptionJpaEntity.class, reception.id());
        }
        entity.apply(reception);
        // Registrar lo recibido solo cambia las líneas: se fuerza la versión de la recepción para que una
        // conformidad y un cambio de línea simultáneos no se pisen (el segundo falla con 409).
        entityManager.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
