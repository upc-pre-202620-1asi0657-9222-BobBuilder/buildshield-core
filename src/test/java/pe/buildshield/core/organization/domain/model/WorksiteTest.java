package pe.buildshield.core.organization.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.buildshield.commons.error.ValidationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorksiteTest {

    private static final Location LIMA = new Location("Av. Javier Prado 123", "San Isidro", "Lima", null, null);
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate JUN_30 = LocalDate.of(2027, 6, 30);

    @Test
    void registers_with_location_and_dates() {
        Worksite worksite = Worksite.register("  Torre Norte ", LIMA, NOV_1, JUN_30);

        assertThat(worksite.id()).isNull();
        assertThat(worksite.name()).isEqualTo("Torre Norte");
        assertThat(worksite.location()).isEqualTo(LIMA);
        assertThat(worksite.startDate()).isEqualTo(NOV_1);
        assertThat(worksite.endDate()).isEqualTo(JUN_30);
    }

    @Test
    void end_date_cannot_be_before_start_date() {
        assertThatThrownBy(() -> Worksite.register("Torre Norte", LIMA, JUN_30, NOV_1))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", Worksite.INVALID_DATE_RANGE);
    }

    @Test
    void end_date_may_equal_start_date_or_be_absent() {
        assertThat(Worksite.register("Un día", LIMA, NOV_1, NOV_1).endDate()).isEqualTo(NOV_1);
        assertThat(Worksite.register("Abierta", LIMA, NOV_1, null).endDate()).isNull();
    }

    @Test
    void start_date_and_name_are_required() {
        assertThatThrownBy(() -> Worksite.register("Torre", LIMA, null, JUN_30))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", Worksite.INVALID_DATE_RANGE);
        assertThatThrownBy(() -> Worksite.register(" ", LIMA, NOV_1, JUN_30))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_NAME");
        assertThatThrownBy(() -> Worksite.register("x".repeat(151), LIMA, NOV_1, JUN_30))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_changes_only_given_fields_and_keeps_the_date_rule() {
        Worksite worksite = Worksite.restore(UUID.randomUUID(), "Torre Norte", LIMA, NOV_1, JUN_30, 0L);

        worksite.update(null, null, null, LocalDate.of(2027, 12, 31));
        assertThat(worksite.endDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(worksite.name()).isEqualTo("Torre Norte");

        assertThatThrownBy(() -> worksite.update(null, null, null, LocalDate.of(2026, 10, 1)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", Worksite.INVALID_DATE_RANGE);
        assertThatThrownBy(() -> worksite.update(null, null, LocalDate.of(2028, 1, 1), null))
                .as("mover el inicio después del fin vigente")
                .isInstanceOf(ValidationException.class);
        assertThat(worksite.endDate()).as("un cambio rechazado no modifica nada").isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(worksite.startDate()).isEqualTo(NOV_1);
    }

    @Test
    void update_can_rename_and_relocate() {
        Worksite worksite = Worksite.restore(UUID.randomUUID(), "Torre Norte", LIMA, NOV_1, null, 0L);
        Location callao = new Location("Av. Argentina 2500", "Callao", "Callao", -12.05, -77.12);

        worksite.update("Torre Norte II", callao, null, null);

        assertThat(worksite.name()).isEqualTo("Torre Norte II");
        assertThat(worksite.location()).isEqualTo(callao);
        assertThat(worksite.version()).isZero();
    }

    @Test
    void location_requires_address_district_and_city() {
        assertThatThrownBy(() -> new Location(" ", "San Isidro", "Lima", null, null))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_LOCATION");
        assertThatThrownBy(() -> new Location("Av. 1", null, null, null, null))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getDetails()).hasSize(2));
        assertThatThrownBy(() -> new Location("x".repeat(201), "San Isidro", "Lima", null, null))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @CsvSource({"-90.1, 0", "90.1, 0", "0, -180.1", "0, 180.1"})
    void coordinates_must_be_in_range(double latitude, double longitude) {
        assertThatThrownBy(() -> new Location("Av. 1", "San Isidro", "Lima", latitude, longitude))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void coordinates_go_together() {
        assertThatThrownBy(() -> new Location("Av. 1", "San Isidro", "Lima", -12.0, null))
                .isInstanceOf(ValidationException.class);
        assertThat(new Location(" Av. 1 ", "San Isidro", "Lima", -90.0, 180.0).address()).isEqualTo("Av. 1");
    }
}
