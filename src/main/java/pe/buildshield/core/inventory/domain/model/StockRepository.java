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

    /**
     * Pasa {@code quantity} de disponible a reservado, solo si la versión sigue siendo
     * {@code expectedVersion} y hay disponible suficiente.
     *
     * @return {@code false} si otra operación cambió el ítem o ya no alcanza (no hubo efecto)
     */
    boolean tryReserve(UUID stockItemId, long expectedVersion, Quantity quantity);

    /**
     * Saca {@code quantity} de lo reservado (salida por despacho), solo si la versión sigue siendo
     * {@code expectedVersion} y hay reservado suficiente.
     *
     * @return {@code false} si otra operación cambió el ítem o ya no alcanza (no hubo efecto)
     */
    boolean tryConsumeReserved(UUID stockItemId, long expectedVersion, Quantity quantity);

    /** Suma al ítem (lo crea si no existe) y devuelve el ítem resultante. */
    StockItem add(UUID locationId, UUID materialId, Quantity quantity);

    void record(StockMovement movement);

    void saveReservation(StockReservation reservation);

    Optional<StockReservation> findReservation(UUID orderLineId);

    /**
     * Suma {@code quantity} a lo consumido de la reserva de la línea, solo si sigue vigente y le queda
     * suficiente; si queda consumida por completo pasa a CONSUMED.
     *
     * @return {@code false} si la reserva ya no admite ese consumo (no hubo efecto)
     */
    boolean tryConsumeReservation(UUID orderLineId, Quantity quantity);
}
