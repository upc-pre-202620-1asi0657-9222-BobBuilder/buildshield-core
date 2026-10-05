package pe.buildshield.core.organization.domain.model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacenes y centros de acopio de la organización del contexto. */
public interface WarehouseRepository {

    Optional<Warehouse> findById(UUID id);

    List<Warehouse> findAll();

    List<Warehouse> findAllActive();

    List<Warehouse> findAllById(Collection<UUID> ids);

    Warehouse save(Warehouse warehouse);
}
