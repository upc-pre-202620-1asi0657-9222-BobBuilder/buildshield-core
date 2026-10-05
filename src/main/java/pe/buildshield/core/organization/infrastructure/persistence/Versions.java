package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.persistence.AuditableAbstractAggregateRoot;

import java.util.Objects;
import java.util.UUID;

/** Carga la entidad para actualizarla, verificando que la versión del dominio siga vigente (bloqueo optimista). */
final class Versions {

    private Versions() {
    }

    static <E extends AuditableAbstractAggregateRoot<E>> E load(JpaRepository<E, UUID> jpa, UUID id, Long expectedVersion,
            Class<E> type) {
        E entity = jpa.findById(id).orElseThrow(() -> new ResourceNotFoundException("El recurso " + id + " no existe"));
        if (!Objects.equals(entity.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(type, id);
        }
        return entity;
    }
}
