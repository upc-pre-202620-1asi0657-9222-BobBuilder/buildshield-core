package pe.buildshield.core.iam.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.commons.error.ValidationException;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.PasswordPolicy;
import pe.buildshield.core.iam.domain.model.PasswordResetToken;
import pe.buildshield.core.iam.domain.model.TokenRepositories.PasswordResetTokenRepository;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * US05: recuperación de contraseña con un enlace de un solo uso que vence. La solicitud responde
 * igual exista o no el correo, para no revelar qué correos están registrados.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final EmailPort email;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final IamProperties properties;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository resetTokens,
            RefreshTokenRepository refreshTokens, EmailPort email, PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate, Clock clock, IamProperties properties) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.email = email;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.properties = properties;
    }

    public void requestReset(String rawEmail) {
        Optional<User> user = parseEmail(rawEmail).flatMap(address ->
                TenantContext.callAsSystem(() -> transactionTemplate.execute(status -> users.findByEmail(address))))
                .filter(User::canSignIn);
        if (user.isEmpty()) {
            log.info("Solicitud de recuperación de contraseña para un correo no registrado o inactivo");
            return;
        }
        String rawToken = OpaqueTokens.newToken();
        PasswordResetToken token = PasswordResetToken.issue(user.get().id(), user.get().organizationId(),
                OpaqueTokens.hash(rawToken), clock.instant(), properties.passwordResetTtl());
        TenantContext.runAsSystem(() -> transactionTemplate.executeWithoutResult(status -> resetTokens.save(token)));
        email.sendPasswordReset(user.get().email(), user.get().fullName(),
                properties.passwordResetUrl() + "?token=" + rawToken, token.expiresAt());
    }

    /** Cambia la contraseña, invalida el enlace y cierra las sesiones renovables del usuario. */
    public void confirmReset(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword, "newPassword");
        if (rawToken == null || rawToken.isBlank()) {
            throw PasswordResetToken.invalid();
        }
        String newHash = passwordEncoder.encode(newPassword);
        Instant now = clock.instant();
        TenantContext.runAsSystem(() -> transactionTemplate.executeWithoutResult(status -> {
            PasswordResetToken token = resetTokens.findByTokenHashForUpdate(OpaqueTokens.hash(rawToken))
                    .orElseThrow(PasswordResetToken::invalid);
            token.use(now);
            User user = users.findById(token.userId()).orElseThrow(PasswordResetToken::invalid);
            user.changePasswordHash(newHash);
            users.save(user);
            resetTokens.save(token);
            refreshTokens.revokeAllActive(user.id(), now);
        }));
    }

    private static Optional<EmailAddress> parseEmail(String rawEmail) {
        try {
            return Optional.of(new EmailAddress(rawEmail));
        } catch (ValidationException ex) {
            return Optional.empty();
        }
    }
}
