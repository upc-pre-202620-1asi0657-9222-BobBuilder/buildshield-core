package pe.buildshield.core.organization.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationTest {

    @Test
    void ruc_accepts_eleven_digits_and_trims() {
        assertThat(new Ruc(" 20123456789 ").value()).isEqualTo("20123456789");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2012345", "201234567890", "2012345678A", "20-23456789"})
    void ruc_rejects_anything_but_eleven_digits(String value) {
        assertThatThrownBy(() -> new Ruc(value))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_RUC");
    }

    @Test
    void registers_with_trimmed_legal_name() {
        UUID id = UUID.randomUUID();

        Organization organization = Organization.register(id, new Ruc("20123456789"), "  Constructora Andina SAC ");

        assertThat(organization.id()).isEqualTo(id);
        assertThat(organization.ruc().value()).isEqualTo("20123456789");
        assertThat(organization.legalName()).isEqualTo("Constructora Andina SAC");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void legal_name_is_required(String legalName) {
        assertThatThrownBy(() -> Organization.register(UUID.randomUUID(), new Ruc("20123456789"), legalName))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_LEGAL_NAME");
    }

    @Test
    void legal_name_has_a_maximum_length() {
        String tooLong = "x".repeat(Organization.MAX_LEGAL_NAME + 1);

        assertThatThrownBy(() -> Organization.register(UUID.randomUUID(), new Ruc("20123456789"), tooLong))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void restore_keeps_the_stored_values() {
        UUID id = UUID.randomUUID();

        Organization organization = Organization.restore(id, new Ruc("20123456789"), "Andina");

        assertThat(organization.id()).isEqualTo(id);
        assertThat(organization.legalName()).isEqualTo("Andina");
    }
}
