package pe.buildshield.core.organization.domain.model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Obras de la organización del contexto. */
public interface WorksiteRepository {

    Optional<Worksite> findById(UUID id);

    List<Worksite> findAll();

    List<Worksite> findAllById(Collection<UUID> ids);

    Worksite save(Worksite worksite);
}
