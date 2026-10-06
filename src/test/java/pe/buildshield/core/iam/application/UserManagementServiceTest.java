package pe.buildshield.core.iam.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.shared.tenant.MissingTenantContextException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;
import pe.buildshield.core.shared.error.ResourceNotFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserManagementServiceTest {

    private final pe.buildshield.core.audit.AuditTrail audit = pe.buildshield.core.support.AuditTestSupport.noop();
    private static final UUID ORG = UUID.randomUUID();

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-06T12:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private final UserManagementService service = new UserManagementService(users, encoder, refreshTokens,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC), audit);

    @BeforeEach
    void admin() {
        TenantContext.set(new TenantInfo(ORG, ADMIN, "ADMINISTRATOR"));
        when(encoder.encode(anyString())).thenReturn("$2a$12$hash");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void creates_the_user_in_the_administrator_organization() {
        UUID id = UUID.randomUUID();
        when(users.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.restore(id, user.organizationId(), user.email(), user.fullName(), user.role(),
                    user.passwordHash(), true, 0L);
        });

        UserManagementService.UserView view = service.create(
                new UserManagementService.CreateUserCommand("Rosa Quispe", "Rosa@Andina.pe", "WAREHOUSE_MANAGER", "Almacen123"));

        assertThat(view).isEqualTo(new UserManagementService.UserView(id, "rosa@andina.pe", "Rosa Quispe",
                Role.WAREHOUSE_MANAGER, true));
        verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.organizationId().equals(ORG) && user.passwordHash().equals("$2a$12$hash")));
    }

    @Test
    void registered_email_is_a_conflict() {
        when(users.existsByEmailInAnyOrganization(new EmailAddress("rosa@andina.pe"))).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new UserManagementService.CreateUserCommand("Rosa", "rosa@andina.pe", "SITE_MANAGER", "Obra12345")))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_REGISTERED");
        verify(users, never()).save(any());
    }

    @Test
    void concurrent_duplicate_email_is_a_conflict() {
        when(users.save(any())).thenThrow(new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"uk_users_email\"")));

        assertThatThrownBy(() -> service.create(
                new UserManagementService.CreateUserCommand("Rosa", "rosa@andina.pe", "SITE_MANAGER", "Obra12345")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void invalid_role_or_weak_password_are_rejected() {
        assertThatThrownBy(() -> service.create(
                new UserManagementService.CreateUserCommand("Pedro", "pedro@andina.pe", "gerente", "Gerente123")))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "INVALID_ROLE");
        assertThatThrownBy(() -> service.create(
                new UserManagementService.CreateUserCommand("Pedro", "pedro@andina.pe", "SITE_MANAGER", "123")))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "WEAK_PASSWORD");
        verify(users, never()).save(any());
    }

    @Test
    void creating_without_organization_fails_safely() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.create(
                new UserManagementService.CreateUserCommand("Pedro", "pedro@andina.pe", "SITE_MANAGER", "Obra12345")))
                .isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void lists_the_users_returned_for_the_organization() {
        when(users.findAll()).thenReturn(List.of(User.restore(UUID.randomUUID(), ORG, new EmailAddress("ana@andina.pe"),
                "Ana", Role.ADMINISTRATOR, "h", true, 0L)));

        assertThat(service.list()).extracting(UserManagementService.UserView::email).containsExactly("ana@andina.pe");
    }

    // ---------- US04: desactivar y cambiar el rol ----------

    private static User stored(UUID id, Role role, boolean active) {
        return User.restore(id, ORG, new EmailAddress("rosa@andina.pe"), "Rosa", role, "h", active, 1L);
    }

    @Test
    void deactivating_a_user_revokes_its_sessions() {
        UUID rosa = UUID.randomUUID();
        when(users.findById(rosa)).thenReturn(java.util.Optional.of(stored(rosa, Role.WAREHOUSE_MANAGER, true)));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserManagementService.UserView view = service.update(rosa, new UserManagementService.UpdateUserCommand(false, null));

        assertThat(view.active()).isFalse();
        verify(refreshTokens).revokeAllActive(rosa, NOW);
        verify(audit).recorded(org.mockito.ArgumentMatchers.eq("USER_DEACTIVATED"), anyString(), any());
    }

    @Test
    void changing_the_role_revokes_sessions_and_nothing_changes_without_differences() {
        UUID rosa = UUID.randomUUID();
        when(users.findById(rosa)).thenReturn(java.util.Optional.of(stored(rosa, Role.WAREHOUSE_MANAGER, true)));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(rosa, new UserManagementService.UpdateUserCommand(null, "SITE_MANAGER")).role())
                .isEqualTo(Role.SITE_MANAGER);
        verify(refreshTokens).revokeAllActive(rosa, NOW);

        when(users.findById(rosa)).thenReturn(java.util.Optional.of(stored(rosa, Role.SITE_MANAGER, true)));
        service.update(rosa, new UserManagementService.UpdateUserCommand(true, "SITE_MANAGER"));
        verify(users, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void the_administrator_cannot_deactivate_itself_or_change_its_role() {
        when(users.findById(ADMIN)).thenReturn(java.util.Optional.of(stored(ADMIN, Role.ADMINISTRATOR, true)));

        assertThatThrownBy(() -> service.update(ADMIN, new UserManagementService.UpdateUserCommand(false, null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", UserManagementService.CANNOT_CHANGE_OWN_ACCOUNT);
        assertThatThrownBy(() -> service.update(ADMIN, new UserManagementService.UpdateUserCommand(null, "SITE_MANAGER")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void update_requires_a_change_an_existing_user_and_a_valid_role() {
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new UserManagementService.UpdateUserCommand(null, " ")))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "EMPTY_UPDATE");
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new UserManagementService.UpdateUserCommand(null, "gerente")))
                .isInstanceOf(ValidationException.class);
        when(users.findById(any())).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new UserManagementService.UpdateUserCommand(false, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }
}
