package pe.buildshield.core.ordering.domain.model;

/**
 * Patrón State del pedido. Cada estado decide qué transiciones admite; lo que no admite lanza
 * {@link InvalidOrderTransitionException}.
 *
 * <pre>
 * Registrado ──aprobar──► EnRevision ──despacho──► ParcialmenteAtendido ──despacho──► Atendido ──cerrar──► Cerrado
 *     │                       │   └──────────despacho completo──────────────────────────▲
 *     └──rechazar/cancelar──► Cancelado ◄──cancelar──┘
 * </pre>
 */
public sealed interface OrderState permits OrderState.Registered, OrderState.InReview, OrderState.PartiallyFulfilled,
        OrderState.Fulfilled, OrderState.Closed, OrderState.Cancelled {

    OrderStatus status();

    default OrderState approve() {
        throw new InvalidOrderTransitionException(status(), "aprobar");
    }

    default OrderState reject() {
        throw new InvalidOrderTransitionException(status(), "rechazar");
    }

    default OrderState cancel() {
        throw new InvalidOrderTransitionException(status(), "cancelar");
    }

    default boolean acceptsDispatch() {
        return false;
    }

    /** Estado después de un despacho; {@code fullyDispatched} si ya no queda nada pendiente. */
    default OrderState dispatched(boolean fullyDispatched) {
        throw new InvalidOrderTransitionException(status(), "despachar");
    }

    default OrderState close() {
        throw new InvalidOrderTransitionException(status(), "cerrar");
    }

    static OrderState of(OrderStatus status) {
        return switch (status) {
            case REGISTERED -> new Registered();
            case IN_REVIEW -> new InReview();
            case PARTIALLY_FULFILLED -> new PartiallyFulfilled();
            case FULFILLED -> new Fulfilled();
            case CLOSED -> new Closed();
            case CANCELLED -> new Cancelled();
        };
    }

    /** Recién creado, pendiente de aprobación. */
    record Registered() implements OrderState {
        public OrderStatus status() {
            return OrderStatus.REGISTERED;
        }

        public OrderState approve() {
            return new InReview();
        }

        public OrderState reject() {
            return new Cancelled();
        }

        public OrderState cancel() {
            return new Cancelled();
        }
    }

    /** Aprobado: el almacén lo prepara y despacha. */
    record InReview() implements OrderState {
        public OrderStatus status() {
            return OrderStatus.IN_REVIEW;
        }

        public boolean acceptsDispatch() {
            return true;
        }

        public OrderState dispatched(boolean fullyDispatched) {
            return fullyDispatched ? new Fulfilled() : new PartiallyFulfilled();
        }

        public OrderState cancel() {
            return new Cancelled();
        }
    }

    record PartiallyFulfilled() implements OrderState {
        public OrderStatus status() {
            return OrderStatus.PARTIALLY_FULFILLED;
        }

        public boolean acceptsDispatch() {
            return true;
        }

        public OrderState dispatched(boolean fullyDispatched) {
            return fullyDispatched ? new Fulfilled() : this;
        }
    }

    /** Todo despachado (o lo pendiente cancelado); se cierra al completar la recepción. */
    record Fulfilled() implements OrderState {
        public OrderStatus status() {
            return OrderStatus.FULFILLED;
        }

        public OrderState close() {
            return new Closed();
        }
    }

    record Closed() implements OrderState {
        public OrderStatus status() {
            return OrderStatus.CLOSED;
        }
    }

    record Cancelled() implements OrderState {
        public OrderStatus status() {
            return OrderStatus.CANCELLED;
        }
    }
}
