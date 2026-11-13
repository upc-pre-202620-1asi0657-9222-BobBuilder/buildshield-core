package pe.buildshield.core.dispatch.domain.model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Despachos de la organización del contexto. */
public interface DispatchRepository {

    Optional<Dispatch> findById(UUID id);

    /** Todos, del más reciente al más antiguo. */
    List<Dispatch> findAll();

    /** Despachos cuyo almacén de origen o cuya obra de destino está entre {@code siteIds}. */
    List<Dispatch> findBySites(Collection<UUID> siteIds);

    Dispatch save(Dispatch dispatch);
}
