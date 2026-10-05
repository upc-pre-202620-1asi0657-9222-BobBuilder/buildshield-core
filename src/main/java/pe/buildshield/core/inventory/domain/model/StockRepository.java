package pe.buildshield.core.inventory.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Existencias de la organización del contexto. Las lecturas para descontar van siempre a la base, sin caché. */
public interface StockRepository {

    Optional<StockItem> find(UUID locationId, UUID materialId);

    List<StockItem> findAll();

    /**
     * Descuenta solo si la versión sigue siendo {@code expectedVersion} y hay cantidad suficiente.
     *
     * @return {@code false} si otra operación cambió el ítem o ya no alcanza (no hubo efecto)
     */
    boolean tryDeduct(UUID stockItemId, long expectedVersion, Quantity quantity);

    /** Suma al ítem (lo crea si no existe) y devuelve el ítem resultante. */
    StockItem add(UUID locationId, UUID materialId, Quantity quantity);

    void record(StockMovement movement);
}
