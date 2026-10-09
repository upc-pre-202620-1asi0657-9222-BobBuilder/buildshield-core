package pe.buildshield.core.ordering.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** pending = requested - dispatched - cancelled; las cantidades solicitadas y despachadas son > 0. */
class OrderLineTest {

    private static final UUID CEMENT = UUID.randomUUID();

    private static OrderLine line(String requested) {
        return OrderLine.request(CEMENT, "CEM-001", "BAG", new BigDecimal(requested));
    }

    private static BigDecimal qty(String value) {
        return new BigDecimal(value);
    }

    @Test
    void new_line_has_everything_pending() {
        OrderLine line = line("50");

        assertThat(line.requested()).isEqualByComparingTo("50");
        assertThat(line.dispatched()).isZero();
        assertThat(line.cancelled()).isZero();
        assertThat(line.received()).isZero();
        assertThat(line.pending()).isEqualByComparingTo("50");
        assertThat(line.isComplete()).isFalse();
    }

    @Test
    void pending_is_requested_minus_dispatched_minus_cancelled() {
        OrderLine line = OrderLine.restore(CEMENT, "CEM-001", "BAG", qty("100"), qty("30"), qty("20"), qty("10"));

        assertThat(line.pending()).isEqualByComparingTo("50");
    }

    @Test
    void dispatching_reduces_pending_and_completes_the_line() {
        OrderLine line = line("50");

        line.registerDispatch(qty("20"));
        assertThat(line.pending()).isEqualByComparingTo("30");
        assertThat(line.dispatched()).isEqualByComparingTo("20");

        line.registerDispatch(qty("30"));
        assertThat(line.pending()).isZero();
        assertThat(line.isComplete()).isTrue();
    }

    @Test
    void cannot_dispatch_more_than_pending() {
        OrderLine line = line("50");
        line.registerDispatch(qty("45"));

        assertThatThrownBy(() -> line.registerDispatch(qty("5.001")))
                .isInstanceOf(pe.buildshield.core.shared.error.ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "DISPATCH_EXCEEDS_PENDING");
        assertThat(line.dispatched()).isEqualByComparingTo("45");
    }

    @Test
    void cancelling_the_rest_keeps_what_was_dispatched() {
        OrderLine line = line("50");
        line.registerDispatch(qty("20"));

        line.cancelRemaining();

        assertThat(line.cancelled()).isEqualByComparingTo("30");
        assertThat(line.dispatched()).isEqualByComparingTo("20");
        assertThat(line.pending()).isZero();
        assertThat(line.isComplete()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.001", "1.0001"})
    void requested_quantity_must_be_positive_with_up_to_three_decimals(String requested) {
        assertThatThrownBy(() -> line(requested))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void dispatched_quantity_must_be_positive(String dispatched) {
        OrderLine line = line("50");

        assertThatThrownBy(() -> line.registerDispatch(qty(dispatched))).isInstanceOf(ValidationException.class);
    }

    @Test
    void requested_quantity_is_required_and_accepts_decimals() {
        assertThatThrownBy(() -> OrderLine.request(CEMENT, "CEM-001", "BAG", null)).isInstanceOf(ValidationException.class);
        assertThat(line("2.5").requested()).isEqualTo(new BigDecimal("2.500"));
    }

    @Test
    void received_cannot_exceed_dispatched() {
        OrderLine line = line("50");
        line.registerDispatch(qty("20"));

        line.registerReceived(qty("19.5"));
        assertThat(line.received()).isEqualByComparingTo("19.5");
        assertThatThrownBy(() -> line.registerReceived(qty("1")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "RECEIVED_EXCEEDS_DISPATCHED");
    }

    @Test
    void stored_quantities_must_be_consistent() {
        assertThatThrownBy(() -> OrderLine.restore(CEMENT, "CEM-001", "BAG", qty("10"), qty("8"), qty("3"), qty("0")))
                .as("despachado + cancelado > solicitado").isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OrderLine.restore(CEMENT, "CEM-001", "BAG", qty("10"), qty("5"), qty("0"), qty("6")))
                .as("recibido > despachado").isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OrderLine.restore(CEMENT, "CEM-001", "BAG", qty("10"), qty("-1"), qty("0"), qty("0")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keeps_material_sku_and_unit() {
        OrderLine line = line("1");

        assertThat(line.materialId()).isEqualTo(CEMENT);
        assertThat(line.sku()).isEqualTo("CEM-001");
        assertThat(line.unit()).isEqualTo("BAG");
    }
}
