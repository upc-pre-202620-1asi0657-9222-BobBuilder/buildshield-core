package pe.buildshield.core.dispatch.domain.model;

/**
 * Patrón State del despacho. Cada estado decide qué operaciones admite; lo que no admite lanza
 * {@link InvalidDispatchTransitionException}.
 *
 * <pre>
 * Preparado ──salir──► EnTransito ──recibir──► Recibido
 *     │  (transportista, pesaje de salida)
 *     └──anular (Sprint 3)──► Anulado
 * </pre>
 */
public sealed interface DispatchState permits DispatchState.Prepared, DispatchState.InTransit, DispatchState.Received,
        DispatchState.Cancelled {

    DispatchStatus status();

    /** ¿Admite cambios de preparación (transportista y pesaje de salida)? */
    default boolean isEditable() {
        return false;
    }

    default DispatchState depart() {
        throw new InvalidDispatchTransitionException(status(), "despachar");
    }

    default DispatchState receive() {
        throw new InvalidDispatchTransitionException(status(), "recibir");
    }

    static DispatchState of(DispatchStatus status) {
        return switch (status) {
            case PREPARED -> new Prepared();
            case IN_TRANSIT -> new InTransit();
            case RECEIVED -> new Received();
            case CANCELLED -> new Cancelled();
        };
    }

    /** En el almacén: se registra el transportista y el pesaje de salida. */
    record Prepared() implements DispatchState {
        public DispatchStatus status() {
            return DispatchStatus.PREPARED;
        }

        public boolean isEditable() {
            return true;
        }

        public DispatchState depart() {
            return new InTransit();
        }
    }

    /** Salió del almacén: la obra lo recibe. */
    record InTransit() implements DispatchState {
        public DispatchStatus status() {
            return DispatchStatus.IN_TRANSIT;
        }

        public DispatchState receive() {
            return new Received();
        }
    }

    record Received() implements DispatchState {
        public DispatchStatus status() {
            return DispatchStatus.RECEIVED;
        }
    }

    /** Reservado para la anulación de despachos del Sprint 3. */
    record Cancelled() implements DispatchState {
        public DispatchStatus status() {
            return DispatchStatus.CANCELLED;
        }
    }
}
