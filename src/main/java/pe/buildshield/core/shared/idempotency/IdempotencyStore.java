package pe.buildshield.core.shared.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacén de claves de idempotencia. Todas las operaciones se llaman dentro de la transacción que
 * abre {@link IdempotencyKeyFilter}, la misma en la que ocurre el efecto de negocio.
 */
public interface IdempotencyStore {

    /**
     * Reserva la clave hasta que termine la transacción actual, sin esperar.
     *
     * @return {@code false} si otra transacción la tiene reservada (operación en curso)
     */
    boolean tryLock(UUID organizationId, UUID key);

    /** Busca el resultado guardado de la clave, ignorando los creados antes de {@code notBefore}. */
    Optional<IdempotencyRecord> find(UUID organizationId, UUID key, Instant notBefore);

    /** Guarda (o reemplaza, si había uno vencido) el resultado de la clave. */
    void save(IdempotencyRecord record);

    /** Borra los resultados creados antes de {@code threshold}; devuelve cuántos borró. */
    int deleteCreatedBefore(Instant threshold);
}
