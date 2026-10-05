package pe.buildshield.core.iam.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.PasswordPolicy;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;

import java.util.List;
import java.util.UUID;

/**
 * US04: el administrador crea y lista los usuarios de su organización (la del token). La
 * restricción de rol está en el controlador; aquí todo queda acotado a la organización del contexto.
 */
@Service
public class UserManagementService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
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
            return UserView.of(created);
        } catch (DataIntegrityViolationException ex) {
            throw UniqueConstraints.translate(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll().stream().map(UserView::of).toList();
    }

    public record CreateUserCommand(String fullName, String email, String role, String password) {
    }

    public record UserView(UUID id, String email, String fullName, Role role, boolean active) {

        static UserView of(User user) {
            return new UserView(user.id(), user.email().value(), user.fullName(), user.role(), user.active());
        }
    }
}
