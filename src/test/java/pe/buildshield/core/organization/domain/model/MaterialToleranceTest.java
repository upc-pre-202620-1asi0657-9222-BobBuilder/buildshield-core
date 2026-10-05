package pe.buildshield.core.organization.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.commons.error.ValidationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tolerancia de merma: porcentaje entre 0 y 100 incluidos, con hasta 2 decimales. */
class MaterialToleranceTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "0.01", "2.5", "99.99", "100", "100.00"})
    void accepts_percentages_from_0_to_100_inclusive(String percent) {
        WasteTolerance tolerance = new WasteTolerance(new BigDecimal(percent));

        assertThat(tolerance.percent().scale()).isEqualTo(2);
        assertThat(tolerance.percent()).isEqualByComparingTo(percent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "100.01", "101", "1000"})
    void rejects_percentages_outside_0_to_100(String percent) {
        assertThatThrownBy(() -> new WasteTolerance(new BigDecimal(percent)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", WasteTolerance.INVALID_WASTE_TOLERANCE);
    }

    @Test
    void rejects_more_than_two_decimals_but_accepts_trailing_zeros() {
        assertThatThrownBy(() -> new WasteTolerance(new BigDecimal("2.555")))
                .isInstanceOf(ValidationException.class);
        assertThat(new WasteTolerance(new BigDecimal("2.5000")).percent()).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void tolerance_is_required() {
        assertThatThrownBy(() -> new WasteTolerance(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void same_percentage_is_the_same_tolerance() {
        assertThat(new WasteTolerance(new BigDecimal("2.5"))).isEqualTo(new WasteTolerance(new BigDecimal("2.50")));
    }

    @Test
    void waste_above_the_tolerance_exceeds_it_and_equal_does_not() {
        WasteTolerance tolerance = new WasteTolerance(new BigDecimal("2.5"));

        assertThat(tolerance.isExceededBy(new BigDecimal("2.51"))).isTrue();
        assertThat(tolerance.isExceededBy(new BigDecimal("2.50"))).isFalse();
        assertThat(tolerance.isExceededBy(BigDecimal.ZERO)).isFalse();
        assertThat(new WasteTolerance(BigDecimal.ZERO).isExceededBy(new BigDecimal("0.01"))).isTrue();
    }
}
