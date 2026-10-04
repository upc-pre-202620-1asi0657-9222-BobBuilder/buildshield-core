package pe.buildshield.core.iam.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.commons.error.ValidationException;
import pe.buildshield.commons.security.BuildshieldJwt;
import pe.buildshield.commons.security.JwtTokenIssuer;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.iam.domain.model.AuthenticationFailedException;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.RefreshToken;
import pe.buildshield.core.iam.domain.model.TokenRepositories.AccessTokenBlocklist;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * US02 y US03: inicio de sesión, renovación con rotación del token de renovación y cierre de sesión
 * con revocación del token de acceso hasta que venza.
 */
@Service
public class AuthenticationService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenBlocklist accessTokenBlocklist;
    private final JwtTokenIssuer jwtIssuer;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final IamProperties properties;
    private volatile String unknownUserHash;

    public AuthenticationService(UserRepository users, RefreshTokenRepository refreshTokens,
            AccessTokenBlocklist accessTokenBlocklist, JwtTokenIssuer jwtIssuer, PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate, Clock clock, IamProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokenBlocklist = accessTokenBlocklist;
        this.jwtIssuer = jwtIssuer;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.properties = properties;
    }

    public SessionTokens signIn(String rawEmail, String password) {
        Optional<EmailAddress> email = parseEmail(rawEmail);
        // Aún no hay organización: se busca el correo en todas (modo sistema, solo lectura).
        Optional<User> user = email.flatMap(address ->
                TenantContext.callAsSystem(() -> transactionTemplate.execute(status -> users.findByEmail(address))));

        // Se compara siempre con bcrypt, exista o no el usuario, para no revelar por el tiempo de
        // respuesta qué correos están registrados.
        String hash = user.map(User::passwordHash).orElseGet(this::unknownUserHash);
        boolean matches = passwordEncoder.matches(password == null ? "" : password, hash);
        if (user.isEmpty() || !matches || !user.get().canSignIn()) {
            throw AuthenticationFailedException.invalidCredentials();
        }
        return openSession(user.get());
    }

    public SessionTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        String nextRawToken = OpaqueTokens.newToken();
        Instant now = clock.instant();
        Rotation rotation = TenantContext.callAsSystem(() -> transactionTemplate.execute(status -> {
            RefreshToken current = refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash(rawRefreshToken))
                    .orElseThrow(AuthenticationFailedException::invalidRefreshToken);
            RefreshToken next = current.rotate(OpaqueTokens.hash(nextRawToken), now, properties.refreshTokenTtl());
            User user = users.findById(current.userId())
                    .filter(User::canSignIn)
                    .orElseThrow(AuthenticationFailedException::invalidRefreshToken);
            refreshTokens.save(current);
            refreshTokens.save(next);
            return new Rotation(user, next);
        }));
        return sessionTokens(rotation.user(), nextRawToken, rotation.next());
    }

    /**
     * Revoca el token de renovación (si es del usuario) y registra el token de acceso en la lista de
     * revocación hasta su vencimiento. Se ejecuta con la organización del token.
     */
    public void signOut(UUID userId, String accessTokenId, String rawRefreshToken) {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
                refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash(rawRefreshToken))
                        .filter(token -> token.userId().equals(userId))
                        .ifPresent(token -> {
                            token.revoke(now);
                            refreshTokens.save(token);
                        });
            }
            accessTokenBlocklist.block(accessTokenId,
                    now.plus(BuildshieldJwt.TOKEN_LIFETIME).plus(BuildshieldJwt.CLOCK_SKEW));
        });
    }

    private SessionTokens openSession(User user) {
        String rawRefreshToken = OpaqueTokens.newToken();
        RefreshToken refreshToken = RefreshToken.issue(user.id(), user.organizationId(),
                OpaqueTokens.hash(rawRefreshToken), clock.instant(), properties.refreshTokenTtl());
        TenantContext.runAsSystem(() -> transactionTemplate.executeWithoutResult(status -> refreshTokens.save(refreshToken)));
        return sessionTokens(user, rawRefreshToken, refreshToken);
    }

    private SessionTokens sessionTokens(User user, String rawRefreshToken, RefreshToken refreshToken) {
        JwtTokenIssuer.IssuedToken access = jwtIssuer.issue(
                new TenantInfo(user.organizationId(), user.id(), user.role().name()));
        // El claim exp del JWT va en segundos: se informa el mismo instante que valida el servidor.
        return new SessionTokens(access.value(), access.expiresAt().truncatedTo(ChronoUnit.SECONDS),
                rawRefreshToken, refreshToken.expiresAt());
    }

    private static Optional<EmailAddress> parseEmail(String rawEmail) {
        try {
            return Optional.of(new EmailAddress(rawEmail));
        } catch (ValidationException ex) {
            return Optional.empty();
        }
    }

    private String unknownUserHash() {
        if (unknownUserHash == null) {
            unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return unknownUserHash;
    }

    public record SessionTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
            Instant refreshTokenExpiresAt) {
    }

    private record Rotation(User user, RefreshToken next) {
    }
}
