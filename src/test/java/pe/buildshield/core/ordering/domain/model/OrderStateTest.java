package pe.buildshield.core.ordering.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Matriz completa del patrón State: cada operación en cada estado lleva a un estado o es rechazada. */
class OrderStateTest {

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource({
            "REGISTERED,          approve,          IN_REVIEW",
            "REGISTERED,          reject,           CANCELLED",
            "REGISTERED,          cancel,           CANCELLED",
            "REGISTERED,          dispatchPartial,  RECHAZADA",
            "REGISTERED,          dispatchFull,     RECHAZADA",
            "REGISTERED,          close,            RECHAZADA",
            "IN_REVIEW,           approve,          RECHAZADA",
            "IN_REVIEW,           reject,           RECHAZADA",
            "IN_REVIEW,           cancel,           CANCELLED",
            "IN_REVIEW,           dispatchPartial,  PARTIALLY_FULFILLED",
            "IN_REVIEW,           dispatchFull,     FULFILLED",
            "IN_REVIEW,           close,            RECHAZADA",
            "PARTIALLY_FULFILLED, approve,          RECHAZADA",
            "PARTIALLY_FULFILLED, reject,           RECHAZADA",
            "PARTIALLY_FULFILLED, cancel,           RECHAZADA",
            "PARTIALLY_FULFILLED, dispatchPartial,  PARTIALLY_FULFILLED",
            "PARTIALLY_FULFILLED, dispatchFull,     FULFILLED",
            "PARTIALLY_FULFILLED, close,            RECHAZADA",
            "FULFILLED,           approve,          RECHAZADA",
            "FULFILLED,           reject,           RECHAZADA",
            "FULFILLED,           cancel,           RECHAZADA",
            "FULFILLED,           dispatchPartial,  RECHAZADA",
            "FULFILLED,           dispatchFull,     RECHAZADA",
            "FULFILLED,           close,            CLOSED",
            "CLOSED,              approve,          RECHAZADA",
            "CLOSED,              reject,           RECHAZADA",
            "CLOSED,              cancel,           RECHAZADA",
            "CLOSED,              dispatchFull,     RECHAZADA",
            "CLOSED,              close,            RECHAZADA",
            "CANCELLED,           approve,          RECHAZADA",
            "CANCELLED,           reject,           RECHAZADA",
            "CANCELLED,           cancel,           RECHAZADA",
            "CANCELLED,           dispatchFull,     RECHAZADA",
            "CANCELLED,           close,            RECHAZADA",
    })
    void transition(OrderStatus from, String operation, String expected) {
        OrderState state = OrderState.of(from);
        Function<OrderState, OrderState> op = switch (operation) {
            case "approve" -> OrderState::approve;
            case "reject" -> OrderState::reject;
            case "cancel" -> OrderState::cancel;
            case "dispatchPartial" -> s -> s.dispatched(false);
            case "dispatchFull" -> s -> s.dispatched(true);
            case "close" -> OrderState::close;
            default -> throw new IllegalArgumentException(operation);
        };

        if ("RECHAZADA".equals(expected)) {
            assertThatThrownBy(() -> op.apply(state))
                    .isInstanceOf(InvalidOrderTransitionException.class)
                    .hasFieldOrPropertyWithValue("code", InvalidOrderTransitionException.CODE)
                    .hasMessageContaining(from.label());
        } else {
            assertThat(op.apply(state).status()).isEqualTo(OrderStatus.valueOf(expected));
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void every_status_maps_to_its_state(OrderStatus status) {
        OrderState state = OrderState.of(status);

        assertThat(state.status()).isEqualTo(status);
        assertThat(state.acceptsDispatch())
                .isEqualTo(status == OrderStatus.IN_REVIEW || status == OrderStatus.PARTIALLY_FULFILLED);
    }

    @ParameterizedTest
    @CsvSource({"REGISTERED, Registrado", "IN_REVIEW, EnRevision", "PARTIALLY_FULFILLED, ParcialmenteAtendido",
            "FULFILLED, Atendido", "CLOSED, Cerrado", "CANCELLED, Cancelado"})
    void statuses_have_spanish_labels(OrderStatus status, String label) {
        assertThat(status.label()).isEqualTo(label);
    }
}
