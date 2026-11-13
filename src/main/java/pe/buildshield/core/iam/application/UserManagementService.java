package pe.buildshield.core.iam.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.PasswordPolicy;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * US04: el administrador crea, lista, desactiva y cambia el rol de los usuarios de su organización (la
 * del token). La restricción de rol está en el controlador; aquí todo queda acotado a la organización
 * del contexto.
 */
@Service
public class UserManagementService {

    private final AuditTrail audit;
    private final UserRepository users;
    public static final String CANNOT_CHANGE_OWN_ACCOUNT = "CANNOT_CHANGE_OWN_ACCOUNT";

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public UserManagementService(UserRepository users, PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokens, Clock clock, AuditTrail audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public UserView create(CreateUserCommand command) {
        EmailAddress email = new EmailAddress(command.email());
        Role role = Role.parse(command.role());
        PasswordPolicy.validate(command.password(), "password");
        UUID organizationId = TenantContext.require().organizationId();
        if (users.existsByEmailInAnyOrganization(email)) {
            throw UniqueConstraints.emailAlreadyRegistered();
        }
        try {
            User created = users.save(User.register(organizationId, email, command.fullName(), role,
                    passwordEncoder.encode(command.password())));
            return audit.recorded("USER_CREATED", "USER", UserView.of(created));
        } catch (DataIntegrityViolationException ex) {
            throw UniqueConstraints.translate(ex);
        }
    }

    /**
     * US04: desactiva o reactiva al usuario y cambia su rol. El administrador no se desactiva ni cambia su
     * propio rol (409). Si algo cambia, revoca sus sesiones renovables y sus tokens de acceso dejan de
     * valer (el filtro JWT compara el rol y el estado actuales); un usuario desactivado no inicia sesión.
     */
    @Transactional
    public UserView update(UUID userId, UpdateUserCommand command) {
        if (command.active() == null && (command.role() == null || command.role().isBlank())) {
            throw new ValidationException("EMPTY_UPDATE", "Indica active o role");
        }
        Role role = command.role() == null ? null : Role.parse(command.role());
        TenantInfo requester = TenantContext.require();
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "El usuario " + userId + " no existe"));
        if (user.id().equals(requester.userId())
                && (Boolean.FALSE.equals(command.active()) || (role != null && role != user.role()))) {
            throw new ConflictException(CANNOT_CHANGE_OWN_ACCOUNT,
                    "No puedes desactivarte ni cambiar tu propio rol; pídelo a otro administrador");
        }
        boolean wasActive = user.active();
        if (!user.update(command.active(), role)) {
            return UserView.of(user);
        }
        User saved = users.save(user);
        refreshTokens.revokeAllActive(saved.id(), clock.instant());
        String action = wasActive && !saved.active() ? "USER_DEACTIVATED" : "USER_UPDATED";
        return audit.recorded(action, "USER", UserView.of(saved));
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll().stream().map(UserView::of).toList();
    }

    public record CreateUserCommand(String fullName, String email, String role, String password) {
    }

    public record UpdateUserCommand(Boolean active, String role) {
    }

    public record UserView(UUID id, String email, String fullName, Role role, boolean active) {

        static UserView of(User user) {
            return new UserView(user.id(), user.email().value(), user.fullName(), user.role(), user.active());
        }
    }
}
