package pe.buildshield.core.iam.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.commons.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamValueObjectsTest {

    @Test
    void email_is_normalized_and_can_be_masked() {
        EmailAddress email = new EmailAddress("  Ana.Torres@Andina.PE ");

        assertThat(email.value()).isEqualTo("ana.torres@andina.pe");
        assertThat(email.masked()).isEqualTo("a***@andina.pe");
        assertThat(email).isEqualTo(new EmailAddress("ana.torres@andina.pe"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ana", "ana@", "@andina.pe", "ana@andina", "ana torres@andina.pe"})
    void email_rejects_invalid_formats(String value) {
        assertThatThrownBy(() -> new EmailAddress(value))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_EMAIL");
    }

    @Test
    void email_has_a_maximum_length() {
        assertThatThrownBy(() -> new EmailAddress("a".repeat(250) + "@x.pe")).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Segura123", "abcdefg1", "12345678a", "Contraseña con espacios 1"})
    void password_policy_accepts_strong_passwords(String password) {
        assertThatCode(() -> PasswordPolicy.validate(password, "password")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"corta1", "sinnumeros", "12345678", "        "})
    void password_policy_rejects_weak_passwords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, "password"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "WEAK_PASSWORD");
    }

    @Test
    void password_policy_rejects_passwords_longer_than_bcrypt_supports() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a1".repeat(37), "password"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void role_parses_its_name_ignoring_case() {
        assertThat(Role.parse("warehouse_manager")).isEqualTo(Role.WAREHOUSE_MANAGER);
        assertThat(Role.parse(" SITE_MANAGER ")).isEqualTo(Role.SITE_MANAGER);
        assertThat(Role.ADMINISTRATOR.displayName()).isEqualTo("administrador");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"gerente", "ROLE_ADMINISTRATOR"})
    void role_rejects_unknown_values(String value) {
        assertThatThrownBy(() -> Role.parse(value))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ROLE");
    }

    @Test
    void authentication_failures_have_stable_codes() {
        assertThat(AuthenticationFailedException.invalidCredentials().getCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(AuthenticationFailedException.invalidRefreshToken().getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
    }
}
