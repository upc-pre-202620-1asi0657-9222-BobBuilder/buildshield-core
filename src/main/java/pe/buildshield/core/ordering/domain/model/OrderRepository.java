package pe.buildshield.core.ordering.domain.model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pedidos de la organización del contexto. */
public interface OrderRepository {

    Optional<Order> findById(UUID id);

    List<Order> findAll();

    /** Pedidos cuya obra o cuyo almacén de origen está entre {@code siteIds}. */
    List<Order> findBySites(Collection<UUID> siteIds);

    Order save(Order order);
}
