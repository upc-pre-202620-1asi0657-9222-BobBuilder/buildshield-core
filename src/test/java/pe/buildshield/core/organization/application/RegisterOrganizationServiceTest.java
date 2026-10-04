package pe.buildshield.core.organization.application;

import org.junit.jupiter.api.Test;
import pe.buildshield.commons.error.ConflictException;
import pe.buildshield.commons.error.ValidationException;
import pe.buildshield.core.organization.domain.model.Organization;
import pe.buildshield.core.organization.domain.model.OrganizationRepository;
import pe.buildshield.core.organization.domain.model.Ruc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterOrganizationServiceTest {

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final RegisterOrganizationService service = new RegisterOrganizationService(organizations);

    @Test
    void registers_a_new_organization() {
        UUID id = UUID.randomUUID();

        Organization organization = service.register(id, "20123456789", "Constructora Andina SAC");

        assertThat(organization.id()).isEqualTo(id);
        verify(organizations).save(organization);
    }

    @Test
    void rejects_a_registered_ruc() {
        when(organizations.existsByRuc(new Ruc("20123456789"))).thenReturn(true);

        assertThatThrownBy(() -> service.register(UUID.randomUUID(), "20123456789", "Otra SAC"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", RegisterOrganizationService.RUC_ALREADY_REGISTERED);
        verify(organizations, never()).save(any());
    }

    @Test
    void invalid_ruc_is_rejected_before_touching_the_repository() {
        assertThatThrownBy(() -> service.register(UUID.randomUUID(), "123", "Andina"))
                .isInstanceOf(ValidationException.class);
        verify(organizations, never()).existsByRuc(any());
    }
}
