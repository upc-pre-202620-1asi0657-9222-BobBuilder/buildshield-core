package pe.buildshield.core.inventory.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockModelTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void quantity_is_positive_with_three_decimals() {
        assertThat(Quantity.of("2.5").value()).isEqualTo(new BigDecimal("2.500"));
        assertThat(Quantity.of("0.001").value()).isEqualTo(new BigDecimal("0.001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.0001"})
    void quantity_rejects_zero_negative_or_too_precise(String value) {
        assertThatThrownBy(() -> Quantity.of(value))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
    }

    @Test
    void quantity_is_required() {
        assertThatThrownBy(() -> new Quantity(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void stock_item_knows_if_it_can_cover_a_quantity() {
        StockItem item = new StockItem(ID, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10"), BigDecimal.ZERO, 4);

        assertThat(item.hasAvailable(Quantity.of("10"))).isTrue();
        assertThat(item.hasAvailable(Quantity.of("10.001"))).isFalse();
        assertThat(item.availableAfterDeducting(Quantity.of("7"))).isEqualByComparingTo("3");
    }

    @Test
    void stock_item_is_never_negative() {
        assertThatThrownBy(() -> new StockItem(ID, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-1"),
                BigDecimal.ZERO, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new StockItem(ID, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                new BigDecimal("-1"), 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_movement_cannot_leave_negative_stock() {
        assertThatThrownBy(() -> new StockMovement(ID, StockMovement.Type.DEDUCTION, BigDecimal.TEN,
                new BigDecimal("-1"), null, Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThat(new StockMovement(ID, StockMovement.Type.ENTRY, BigDecimal.TEN, BigDecimal.TEN, "Guía 1",
                Instant.now()).type()).isEqualTo(StockMovement.Type.ENTRY);
    }
}
