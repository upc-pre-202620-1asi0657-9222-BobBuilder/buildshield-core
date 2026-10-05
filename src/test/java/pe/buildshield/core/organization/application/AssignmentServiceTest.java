package pe.buildshield.core.organization.application;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.buildshield.commons.error.ConflictException;
import pe.buildshield.commons.error.ResourceNotFoundException;
import pe.buildshield.commons.error.ValidationException;
import pe.buildshield.core.organization.StaffDirectory;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.StaffAssignment;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.WarehouseType;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID JORGE = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();
    private static final UUID TORRE = UUID.randomUUID();
    private static final UUID CENTRAL = UUID.randomUUID();
    private static final UUID ASSIGNMENT = UUID.randomUUID();

    private final StaffAssignmentRepository assignments = mock(StaffAssignmentRepository.class);
    private final WorksiteRepository worksites = mock(WorksiteRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final StaffDirectory staff = mock(StaffDirectory.class);
    private final SiteVisibility visibility = mock(SiteVisibility.class);
    private final AssignmentService service = new AssignmentService(assignments, worksites, warehouses, staff,
            visibility, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void assigns_a_site_manager_to_a_worksite() {
        givenUser(JORGE, "SITE_MANAGER", true);
        when(worksites.findById(TORRE)).thenReturn(Optional.of(worksite()));
        when(assignments.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        AssignmentService.AssignmentView view = service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WORKSITE, TORRE));

        assertThat(view.id()).isEqualTo(ASSIGNMENT);
        assertThat(view.active()).isTrue();
        assertThat(view.assignedAt()).isEqualTo(NOW);
    }

    @Test
    void assigns_a_warehouse_manager_to_an_active_warehouse() {
        givenUser(ROSA, "WAREHOUSE_MANAGER", true);
        when(warehouses.findById(CENTRAL)).thenReturn(Optional.of(warehouse(true)));
        when(assignments.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        assertThat(service.assign(new AssignmentService.AssignStaff(ROSA, SiteType.WAREHOUSE, CENTRAL)).siteType())
                .isEqualTo(SiteType.WAREHOUSE);
    }

    @Test
    void unknown_inactive_or_foreign_user_is_404() {
        givenUser(JORGE, "SITE_MANAGER", false);

        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WORKSITE, TORRE)))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(UUID.randomUUID(), SiteType.WORKSITE, TORRE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unknown_site_is_404_and_inactive_warehouse_is_409() {
        givenUser(JORGE, "SITE_MANAGER", true);
        givenUser(ROSA, "WAREHOUSE_MANAGER", true);
        when(warehouses.findById(CENTRAL)).thenReturn(Optional.of(warehouse(false)));

        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WORKSITE, TORRE)))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "WORKSITE_NOT_FOUND");
        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(ROSA, SiteType.WAREHOUSE, UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "WAREHOUSE_NOT_FOUND");
        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(ROSA, SiteType.WAREHOUSE, CENTRAL)))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", AssignmentService.SITE_INACTIVE);
        verify(assignments, never()).save(any());
    }

    @Test
    void wrong_role_for_the_site_is_400() {
        givenUser(JORGE, "SITE_MANAGER", true);
        when(warehouses.findById(CENTRAL)).thenReturn(Optional.of(warehouse(true)));

        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WAREHOUSE, CENTRAL)))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", StaffAssignment.ROLE_NOT_ALLOWED_FOR_SITE);
    }

    @Test
    void repeated_active_assignment_is_409_also_when_detected_by_the_database() {
        givenUser(JORGE, "SITE_MANAGER", true);
        when(worksites.findById(TORRE)).thenReturn(Optional.of(worksite()));
        when(assignments.existsActive(JORGE, TORRE)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WORKSITE, TORRE)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", AssignmentService.ASSIGNMENT_ALREADY_ACTIVE);

        when(assignments.existsActive(JORGE, TORRE)).thenReturn(false);
        when(assignments.save(any())).thenThrow(new DataIntegrityViolationException("x", new SQLException(
                "duplicate key value violates unique constraint \"uk_staff_assignments_active_worksite\"")));
        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WORKSITE, TORRE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void other_integrity_violations_propagate() {
        givenUser(JORGE, "SITE_MANAGER", true);
        when(worksites.findById(TORRE)).thenReturn(Optional.of(worksite()));
        DataIntegrityViolationException other = new DataIntegrityViolationException("x", new SQLException("otro"));
        when(assignments.save(any())).thenThrow(other);

        assertThatThrownBy(() -> service.assign(new AssignmentService.AssignStaff(JORGE, SiteType.WORKSITE, TORRE)))
                .isSameAs(other);
    }

    @Test
    void ending_an_assignment_keeps_it_and_only_false_is_accepted() {
        StaffAssignment active = StaffAssignment.restore(ASSIGNMENT, JORGE, SiteType.WORKSITE, TORRE, NOW, null, 0L);
        when(assignments.findById(ASSIGNMENT)).thenReturn(Optional.of(active));
        when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.update(ASSIGNMENT, new AssignmentService.UpdateAssignment(true)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.update(ASSIGNMENT, new AssignmentService.UpdateAssignment(null)))
                .isInstanceOf(ValidationException.class);

        AssignmentService.AssignmentView ended = service.update(ASSIGNMENT, new AssignmentService.UpdateAssignment(false));
        assertThat(ended.active()).isFalse();
        assertThat(ended.endedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new AssignmentService.UpdateAssignment(false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_and_list_respect_visibility() {
        StaffAssignment assignment = StaffAssignment.restore(ASSIGNMENT, JORGE, SiteType.WORKSITE, TORRE, NOW, null, 0L);
        when(assignments.findById(ASSIGNMENT)).thenReturn(Optional.of(assignment));
        when(visibility.canSee(assignment)).thenReturn(false);
        when(visibility.visibleAssignments()).thenReturn(List.of(assignment));

        assertThatThrownBy(() -> service.get(ASSIGNMENT)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ASSIGNMENT_NOT_FOUND");
        assertThat(service.list()).extracting(AssignmentService.AssignmentView::id).containsExactly(ASSIGNMENT);

        when(visibility.canSee(assignment)).thenReturn(true);
        assertThat(service.get(ASSIGNMENT).userId()).isEqualTo(JORGE);
    }

    private void givenUser(UUID id, String role, boolean active) {
        when(staff.findMember(id)).thenReturn(Optional.of(new StaffDirectory.StaffMember(id, role, active)));
    }

    private static Worksite worksite() {
        return Worksite.restore(TORRE, "Torre", new Location("Av. 1", "Lince", "Lima", null, null),
                LocalDate.of(2026, 11, 1), null, 0L);
    }

    private static Warehouse warehouse(boolean active) {
        return Warehouse.restore(CENTRAL, "Central", WarehouseType.WAREHOUSE, "Av. 1", active, 0L);
    }

    private static StaffAssignment withId(StaffAssignment a) {
        return StaffAssignment.restore(ASSIGNMENT, a.userId(), a.siteType(), a.siteId(), a.assignedAt(), a.endedAt(), 0L);
    }
}
