package pe.buildshield.core.organization.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ValidationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffAssignmentTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SITE = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({"SITE_MANAGER, WORKSITE", "WAREHOUSE_MANAGER, WAREHOUSE"})
    void each_manager_goes_to_its_kind_of_site(String role, SiteType siteType) {
        StaffAssignment assignment = StaffAssignment.assign(USER, role, siteType, SITE, NOW);

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.userId()).isEqualTo(USER);
        assertThat(assignment.siteId()).isEqualTo(SITE);
        assertThat(assignment.siteType()).isEqualTo(siteType);
        assertThat(assignment.assignedAt()).isEqualTo(NOW);
        assertThat(assignment.id()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"SITE_MANAGER, WAREHOUSE", "WAREHOUSE_MANAGER, WORKSITE", "ADMINISTRATOR, WORKSITE", "ADMINISTRATOR, WAREHOUSE"})
    void other_role_and_site_combinations_are_rejected(String role, SiteType siteType) {
        assertThatThrownBy(() -> StaffAssignment.assign(USER, role, siteType, SITE, NOW))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", StaffAssignment.ROLE_NOT_ALLOWED_FOR_SITE);
    }

    @Test
    void site_type_is_required() {
        assertThatThrownBy(() -> StaffAssignment.assign(USER, "SITE_MANAGER", null, SITE, NOW))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SITE_TYPE");
    }

    @Test
    void ending_keeps_the_assignment_as_history_and_happens_once() {
        StaffAssignment assignment = StaffAssignment.restore(UUID.randomUUID(), USER, SiteType.WORKSITE, SITE, NOW, null, 0L);

        assignment.end(NOW.plusSeconds(3600));

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.endedAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(assignment.version()).isZero();
        assertThatThrownBy(() -> assignment.end(NOW.plusSeconds(7200)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "ASSIGNMENT_ALREADY_ENDED");
    }

    @Test
    void site_types_know_their_role_and_spanish_name() {
        assertThat(SiteType.WORKSITE.assignableRole()).isEqualTo("SITE_MANAGER");
        assertThat(SiteType.WAREHOUSE.displayName()).isEqualTo("almacén");
    }
}
