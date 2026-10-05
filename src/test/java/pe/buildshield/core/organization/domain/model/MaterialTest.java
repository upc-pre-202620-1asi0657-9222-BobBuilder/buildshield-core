package pe.buildshield.core.organization.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.commons.error.ValidationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialTest {

    private static final WasteTolerance TWO_AND_A_HALF = new WasteTolerance(new BigDecimal("2.5"));

    @Test
    void sku_is_normalized_to_upper_case() {
        assertThat(new Sku(" cem-001 ").value()).isEqualTo("CEM-001");
        assertThat(new Sku("fie_1/2.x").value()).isEqualTo("FIE_1/2.X");
        assertThat(new Sku("cem-001")).isEqualTo(new Sku("CEM-001"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CEM 001", "-CEM", "CEM#1", "Ñ01"})
    void sku_rejects_invalid_formats(String value) {
        assertThatThrownBy(() -> new Sku(value))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SKU");
    }

    @Test
    void sku_has_a_maximum_length() {
        assertThat(new Sku("A".repeat(40)).value()).hasSize(40);
        assertThatThrownBy(() -> new Sku("A".repeat(41))).isInstanceOf(ValidationException.class);
    }

    @Test
    void registers_active_with_unit_and_tolerance() {
        Material material = Material.register(new Sku("cem-001"), " Cemento Portland tipo I ", UnitOfMeasure.BAG, TWO_AND_A_HALF);

        assertThat(material.id()).isNull();
        assertThat(material.sku().value()).isEqualTo("CEM-001");
        assertThat(material.name()).isEqualTo("Cemento Portland tipo I");
        assertThat(material.unit()).isEqualTo(UnitOfMeasure.BAG);
        assertThat(material.wasteTolerance()).isEqualTo(TWO_AND_A_HALF);
        assertThat(material.active()).isTrue();
    }

    @Test
    void unit_and_name_are_required() {
        assertThatThrownBy(() -> Material.register(new Sku("A1"), "Arena", null, TWO_AND_A_HALF))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_UNIT");
        assertThatThrownBy(() -> Material.register(new Sku("A1"), " ", UnitOfMeasure.M3, TWO_AND_A_HALF))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_changes_only_given_fields_and_never_the_sku() {
        Material material = Material.restore(UUID.randomUUID(), new Sku("FIE-012"), "Fierro", UnitOfMeasure.UNIT,
                new WasteTolerance(BigDecimal.ONE), true, 2L);

        material.update(null, null, new WasteTolerance(new BigDecimal("1.5")), null);
        assertThat(material.wasteTolerance().percent()).isEqualTo(new BigDecimal("1.50"));
        assertThat(material.name()).isEqualTo("Fierro");

        material.update("Fierro corrugado", UnitOfMeasure.KG, null, false);
        assertThat(material.name()).isEqualTo("Fierro corrugado");
        assertThat(material.unit()).isEqualTo(UnitOfMeasure.KG);
        assertThat(material.active()).isFalse();
        assertThat(material.sku().value()).isEqualTo("FIE-012");
        assertThat(material.version()).isEqualTo(2L);
    }

    @Test
    void units_have_spanish_names() {
        assertThat(UnitOfMeasure.M3.displayName()).isEqualTo("metro cúbico");
        assertThat(UnitOfMeasure.BAG.displayName()).isEqualTo("bolsa");
    }
}
