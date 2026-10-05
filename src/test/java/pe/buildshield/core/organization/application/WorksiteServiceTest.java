package pe.buildshield.core.organization.application;

import org.junit.jupiter.api.Test;
import pe.buildshield.commons.error.ResourceNotFoundException;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.time.LocalDate;
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

class WorksiteServiceTest {

    private static final Location LIMA = new Location("Av. Javier Prado 123", "San Isidro", "Lima", -12.09, -77.04);
    private static final UUID ID = UUID.randomUUID();

    private final WorksiteRepository worksites = mock(WorksiteRepository.class);
    private final SiteVisibility visibility = mock(SiteVisibility.class);
    private final WorksiteService service = new WorksiteService(worksites, visibility);

    @Test
    void registers_and_returns_the_saved_worksite() {
        when(worksites.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        WorksiteService.WorksiteView view = service.register(new WorksiteService.RegisterWorksite(
                "Torre Norte", LIMA, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 6, 30)));

        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.name()).isEqualTo("Torre Norte");
    }

    @Test
    void get_returns_404_when_not_found_or_not_visible() {
        Worksite worksite = existing();
        when(worksites.findById(ID)).thenReturn(Optional.of(worksite));
        when(visibility.canSee(worksite)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "WORKSITE_NOT_FOUND");
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);

        when(visibility.canSee(worksite)).thenReturn(true);
        assertThat(service.get(ID).name()).isEqualTo("Torre Norte");
    }

    @Test
    void list_returns_what_the_requester_can_see() {
        when(visibility.visibleWorksites()).thenReturn(List.of(existing()));

        assertThat(service.list()).extracting(WorksiteService.WorksiteView::name).containsExactly("Torre Norte");
    }

    @Test
    void update_merges_partial_location_and_dates() {
        when(worksites.findById(ID)).thenReturn(Optional.of(existing()));
        when(worksites.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorksiteService.WorksiteView view = service.update(ID, new WorksiteService.UpdateWorksite(
                null, "Av. Javier Prado 456", null, null, null, null, null, LocalDate.of(2027, 12, 31)));

        assertThat(view.location()).isEqualTo(new Location("Av. Javier Prado 456", "San Isidro", "Lima", -12.09, -77.04));
        assertThat(view.endDate()).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    void update_can_replace_coordinates_together_and_ignores_empty_location() {
        when(worksites.findById(ID)).thenReturn(Optional.of(existing()));
        when(worksites.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorksiteService.WorksiteView moved = service.update(ID, new WorksiteService.UpdateWorksite(
                "Torre Norte II", null, null, null, -12.1, -77.1, null, null));
        WorksiteService.WorksiteView same = service.update(ID, new WorksiteService.UpdateWorksite(
                null, null, null, null, null, null, null, null));

        assertThat(moved.location().latitude()).isEqualTo(-12.1);
        assertThat(moved.name()).isEqualTo("Torre Norte II");
        assertThat(same.location().address()).isEqualTo("Av. Javier Prado 123");
    }

    @Test
    void update_of_an_unknown_worksite_is_404() {
        assertThatThrownBy(() -> service.update(ID, new WorksiteService.UpdateWorksite(
                "X", null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(worksites, never()).save(any());
    }

    private static Worksite existing() {
        return Worksite.restore(ID, "Torre Norte", LIMA, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 6, 30), 0L);
    }

    private static Worksite withId(Worksite worksite) {
        return Worksite.restore(ID, worksite.name(), worksite.location(), worksite.startDate(), worksite.endDate(), 0L);
    }
}
