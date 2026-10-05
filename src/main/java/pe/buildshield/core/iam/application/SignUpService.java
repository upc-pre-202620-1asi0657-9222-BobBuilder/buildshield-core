package pe.buildshield.core.iam.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.PasswordPolicy;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.organization.OrganizationFacade;

import java.util.UUID;

/**
 * US01: registra la organización y su administrador en una sola transacción. Si algo falla (RUC o
 * correo repetidos), no queda nada registrado.
 */
@Service
public class SignUpService {

    private final AuditTrail audit;
    private final OrganizationFacade organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public SignUpService(OrganizationFacade organizations, UserRepository users, PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate, AuditTrail audit) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.audit = audit;
    }

    public SignUpResult signUp(SignUpCommand command) {
        EmailAddress email = new EmailAddress(command.adminEmail());
        PasswordPolicy.validate(command.password(), "password");
        String passwordHash = passwordEncoder.encode(command.password());

        // La organización aún no existe: se abre la transacción en su nombre para que el filtro
        // multiempresa asigne su id al administrador.
        UUID organizationId = UUID.randomUUID();
        TenantInfo registering = new TenantInfo(organizationId, TenantContext.SYSTEM_USER_ID, Role.ADMINISTRATOR.name());
        try {
            return TenantContext.callAs(registering, () -> transactionTemplate.execute(status -> {
                organizations.registerOrganization(organizationId, command.ruc(), command.legalName());
                if (users.existsByEmailInAnyOrganization(email)) {
                    throw UniqueConstraints.emailAlreadyRegistered();
                }
                User administrator = users.save(User.register(organizationId, email, command.adminFullName(),
                        Role.ADMINISTRATOR, passwordHash));
                audit.record("USER_CREATED", "USER", administrator.id(), java.util.Map.of(
                        "email", email.value(), "fullName", administrator.fullName(), "role", "ADMINISTRATOR"));
                return new SignUpResult(organizationId, administrator.id());
            }));
        } catch (DataIntegrityViolationException ex) {
            throw UniqueConstraints.translate(ex);
        }
    }

    public record SignUpCommand(String ruc, String legalName, String adminFullName, String adminEmail, String password) {
    }

    public record SignUpResult(UUID organizationId, UUID administratorId) {
    }
}
