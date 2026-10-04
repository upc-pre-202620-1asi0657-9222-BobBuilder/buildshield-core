package pe.buildshield.core.iam.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.buildshield.commons.security.JwtTokenIssuer;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.iam.domain.model.AuthenticationFailedException;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.RefreshToken;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.TokenRepositories.AccessTokenBlocklist;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.support.TestTransactions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final User ANA = User.restore(USER_ID, ORG, new EmailAddress("ana@andina.pe"), "Ana Torres",
            Role.ADMINISTRATOR, "$2a$12$hash", true, 0L);

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AccessTokenBlocklist blocklist = mock(AccessTokenBlocklist.class);
    private final JwtTokenIssuer jwtIssuer = mock(JwtTokenIssuer.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TestTransactions transactions = new TestTransactions();
    private final AuthenticationService service = new AuthenticationService(users, refreshTokens, blocklist, jwtIssuer,
            encoder, transactions.template(), Clock.fixed(NOW, ZoneOffset.UTC),
            new IamProperties(Duration.ofDays(7), Duration.ofMinutes(30), "http://web/reset"));

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void sign_in_issues_an_access_token_with_the_user_tenant_and_a_hashed_refresh_token() {
        AtomicBoolean lookedUpInSystemMode = new AtomicBoolean();
        when(users.findByEmail(new EmailAddress("ana@andina.pe"))).thenAnswer(invocation -> {
            lookedUpInSystemMode.set(TenantContext.isSystem());
            return Optional.of(ANA);
        });
        when(encoder.matches("Segura123", "$2a$12$hash")).thenReturn(true);
        givenIssuedAccessToken();

        AuthenticationService.SessionTokens tokens = service.signIn(" ANA@andina.pe ", "Segura123");

        assertThat(lookedUpInSystemMode).isTrue();
        assertThat(tokens.accessToken()).isEqualTo("jwt");
        assertThat(tokens.accessTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(tokens.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        verify(jwtIssuer).issue(new TenantInfo(ORG, USER_ID, "ADMINISTRATOR"));
        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(stored.capture());
        assertThat(stored.getValue().tokenHash()).isEqualTo(OpaqueTokens.hash(tokens.refreshToken()))
                .isNotEqualTo(tokens.refreshToken());
        assertThat(stored.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    void wrong_password_is_rejected_with_a_generic_error() {
        when(users.findByEmail(any())).thenReturn(Optional.of(ANA));
        when(encoder.matches("Otra1234", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.signIn("ana@andina.pe", "Otra1234"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void unknown_email_still_runs_bcrypt_and_gets_the_same_error() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("$2a$12$dummy");

        assertThatThrownBy(() -> service.signIn("nadie@andina.pe", "Segura123"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        assertThatThrownBy(() -> service.signIn("nadie@andina.pe", null))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(encoder, times(2)).matches(anyString(), eq("$2a$12$dummy"));
        verify(encoder, times(1)).encode(anyString());
    }

    @Test
    void malformed_email_is_an_authentication_failure_not_a_validation_error() {
        when(encoder.encode(anyString())).thenReturn("$2a$12$dummy");

        assertThatThrownBy(() -> service.signIn("no-es-correo", "Segura123"))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(users, never()).findByEmail(any());
    }

    @Test
    void inactive_user_cannot_sign_in() {
        User inactive = User.restore(USER_ID, ORG, ANA.email(), "Ana", Role.ADMINISTRATOR, "$2a$12$hash", false, 1L);
        when(users.findByEmail(any())).thenReturn(Optional.of(inactive));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.signIn("ana@andina.pe", "Segura123"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refresh_rotates_the_token_and_issues_new_ones() {
        String raw = "token-actual";
        RefreshToken current = RefreshToken.issue(USER_ID, ORG, OpaqueTokens.hash(raw), NOW.minusSeconds(60), Duration.ofDays(7));
        when(refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash(raw))).thenReturn(Optional.of(current));
        when(users.findById(USER_ID)).thenReturn(Optional.of(ANA));
        givenIssuedAccessToken();

        AuthenticationService.SessionTokens tokens = service.refresh(raw);

        assertThat(tokens.refreshToken()).isNotEqualTo(raw);
        assertThat(current.isActive(NOW)).isFalse();
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(2)).save(saved.capture());
        List<RefreshToken> all = saved.getAllValues();
        assertThat(all.get(0)).isSameAs(current);
        assertThat(all.get(1).tokenHash()).isEqualTo(OpaqueTokens.hash(tokens.refreshToken()));
        assertThat(current.replacedBy()).isEqualTo(all.get(1).id());
    }

    @Test
    void refresh_with_unknown_revoked_or_blank_token_fails() {
        RefreshToken revoked = RefreshToken.issue(USER_ID, ORG, OpaqueTokens.hash("viejo"), NOW, Duration.ofDays(7));
        revoked.revoke(NOW);
        when(refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash("viejo"))).thenReturn(Optional.of(revoked));
        when(refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash("desconocido"))).thenReturn(Optional.empty());

        for (String raw : new String[] {"viejo", "desconocido", " ", null}) {
            assertThatThrownBy(() -> service.refresh(raw))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
        }
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void refresh_for_a_user_that_cannot_sign_in_fails_and_rolls_back() {
        RefreshToken current = RefreshToken.issue(USER_ID, ORG, OpaqueTokens.hash("t"), NOW, Duration.ofDays(7));
        when(refreshTokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(current));
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("t")).isInstanceOf(AuthenticationFailedException.class);
        assertThat(transactions.rollbacks).hasValue(1);
    }

    @Test
    void sign_out_revokes_own_refresh_token_and_blocks_the_access_token_until_it_expires() {
        RefreshToken own = RefreshToken.issue(USER_ID, ORG, OpaqueTokens.hash("mio"), NOW, Duration.ofDays(7));
        when(refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash("mio"))).thenReturn(Optional.of(own));

        service.signOut(USER_ID, "jti-1", "mio");

        assertThat(own.isActive(NOW)).isFalse();
        verify(refreshTokens).save(own);
        verify(blocklist).block("jti-1", NOW.plus(Duration.ofMinutes(15)).plusSeconds(30));
    }

    @Test
    void sign_out_ignores_refresh_tokens_of_other_users_but_still_blocks_the_access_token() {
        RefreshToken foreign = RefreshToken.issue(UUID.randomUUID(), ORG, OpaqueTokens.hash("ajeno"), NOW, Duration.ofDays(7));
        when(refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash("ajeno"))).thenReturn(Optional.of(foreign));

        service.signOut(USER_ID, "jti-2", "ajeno");
        service.signOut(USER_ID, "jti-3", null);

        assertThat(foreign.isActive(NOW)).isTrue();
        verify(refreshTokens, never()).save(any());
        verify(blocklist, atLeastOnce()).block(eq("jti-2"), any());
        verify(blocklist).block(eq("jti-3"), any());
    }

    @Test
    void opaque_tokens_are_random_and_hashed_with_sha256() {
        String first = OpaqueTokens.newToken();

        assertThat(first).hasSize(43).isNotEqualTo(OpaqueTokens.newToken());
        assertThat(OpaqueTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private void givenIssuedAccessToken() {
        when(jwtIssuer.issue(any())).thenReturn(
                new JwtTokenIssuer.IssuedToken("jwt", "jti", NOW.plus(Duration.ofMinutes(15))));
    }
}
