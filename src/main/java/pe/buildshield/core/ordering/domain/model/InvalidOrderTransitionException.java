package pe.buildshield.core.ordering.domain.model;

import pe.buildshield.commons.error.ConflictException;

/** La operación no está permitida en el estado actual del pedido. Se responde 409. */
public class InvalidOrderTransitionException extends ConflictException {

    public static final String CODE = "INVALID_ORDER_TRANSITION";

    public InvalidOrderTransitionException(OrderStatus current, String operation) {
        super(CODE, "No se puede " + operation + " un pedido en estado " + current.label());
    }
}
