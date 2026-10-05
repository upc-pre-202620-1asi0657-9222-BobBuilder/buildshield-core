package pe.buildshield.core.iam.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.buildshield.commons.error.ConflictException;
import pe.buildshield.commons.error.ValidationException;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.organization.OrganizationFacade;
import pe.buildshield.core.support.TestTransactions;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SignUpServiceTest {

    private final OrganizationFacade organizations = mock(OrganizationFacade.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TestTransactions transactions = new TestTransactions();
    private final SignUpService service = new SignUpService(organizations, users, encoder, transactions.template());

    private final SignUpService.SignUpCommand command = new SignUpService.SignUpCommand(
            "20123456789", "Constructora Andina SAC", "Ana Torres", "Ana@Andina.pe", "Segura123");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void registers_organization_and_administrator_in_one_transaction_under_the_new_organization() {
        when(encoder.encode("Segura123")).thenReturn("$2a$12$hash");
        AtomicReference<TenantInfo> contextWhileRegistering = new AtomicReference<>();
        doAnswer(invocation -> {
            contextWhileRegistering.set(TenantContext.require());
            return null;
        }).when(organizations).registerOrganization(any(), anyString(), anyString());
        UUID adminId = UUID.randomUUID();
        when(users.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.restore(adminId, user.organizationId(), user.email(), user.fullName(), user.role(),
                    user.passwordHash(), true, 0L);
        });

        SignUpService.SignUpResult result = service.signUp(command);

        assertThat(result.administratorId()).isEqualTo(adminId);
        assertThat(contextWhileRegistering.get().organizationId()).isEqualTo(result.organizationId());
        assertThat(contextWhileRegistering.get().role()).isEqualTo("ADMINISTRATOR");
        verify(organizations).registerOrganization(result.organizationId(), "20123456789", "Constructora Andina SAC");
        verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.role() == Role.ADMINISTRATOR
                        && user.email().equals(new EmailAddress("ana@andina.pe"))
                        && user.passwordHash().equals("$2a$12$hash")
                        && user.organizationId().equals(result.organizationId())));
        assertThat(transactions.commits).hasValue(1);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void registered_email_rolls_back_the_organization() {
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.existsByEmailInAnyOrganization(new EmailAddress("ana@andina.pe"))).thenReturn(true);

        assertThatThrownBy(() -> service.signUp(command))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_REGISTERED");
        assertThat(transactions.rollbacks).hasValue(1);
        assertThat(transactions.commits).hasValue(0);
        verify(users, never()).save(any());
    }

    @Test
    void weak_password_is_rejected_before_opening_a_transaction() {
        SignUpService.SignUpCommand weak = new SignUpService.SignUpCommand(
                "20123456789", "Andina", "Ana", "ana@andina.pe", "corta");

        assertThatThrownBy(() -> service.signUp(weak)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(organizations, users, encoder);
    }

    @Test
    void invalid_email_is_rejected_before_opening_a_transaction() {
        SignUpService.SignUpCommand invalid = new SignUpService.SignUpCommand(
                "20123456789", "Andina", "Ana", "no-es-correo", "Segura123");

        assertThatThrownBy(() -> service.signUp(invalid)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(organizations, users);
    }

    @Test
    void concurrent_duplicate_email_detected_by_the_database_is_a_409() {
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any())).thenThrow(violation("duplicate key value violates unique constraint \"uk_users_email\""));

        assertThatThrownBy(() -> service.signUp(command))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void concurrent_duplicate_ruc_detected_by_the_database_is_a_409() {
        when(encoder.encode(anyString())).thenReturn("hash");
        doAnswer(invocation -> {
            throw violation("duplicate key value violates unique constraint \"uk_organizations_ruc\"");
        }).when(organizations).registerOrganization(any(), eq("20123456789"), anyString());

        assertThatThrownBy(() -> service.signUp(command))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "RUC_ALREADY_REGISTERED");
    }

    @Test
    void other_integrity_violations_propagate() {
        when(encoder.encode(anyString())).thenReturn("hash");
        DataIntegrityViolationException other = violation("null value in column \"full_name\"");
        when(users.save(any())).thenThrow(other);

        assertThatThrownBy(() -> service.signUp(command)).isSameAs(other);
    }

    private static DataIntegrityViolationException violation(String message) {
        return new DataIntegrityViolationException("could not execute statement", new SQLException(message));
    }
}
