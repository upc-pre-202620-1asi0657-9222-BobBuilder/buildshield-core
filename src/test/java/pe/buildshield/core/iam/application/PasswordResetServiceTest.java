package pe.buildshield.core.iam.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.buildshield.commons.error.ValidationException;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.PasswordResetToken;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.TokenRepositories.PasswordResetTokenRepository;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.support.TestTransactions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final EmailPort email = mock(EmailPort.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TestTransactions transactions = new TestTransactions();
    private final PasswordResetService service = new PasswordResetService(users, resetTokens, refreshTokens, email,
            encoder, transactions.template(), Clock.fixed(NOW, ZoneOffset.UTC),
            new IamProperties(Duration.ofDays(7), Duration.ofMinutes(30), "https://app.buildshield.pe/restablecer"));

    private User ana(boolean active) {
        return User.restore(USER_ID, ORG, new EmailAddress("ana@andina.pe"), "Ana Torres", Role.ADMINISTRATOR,
                "$2a$12$old", active, 2L);
    }

    @Test
    void request_stores_only_the_hash_and_emails_a_30_minute_link() {
        when(users.findByEmail(new EmailAddress("ana@andina.pe"))).thenReturn(Optional.of(ana(true)));

        service.requestReset("Ana@Andina.pe");

        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(stored.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(email).sendPasswordReset(eq(new EmailAddress("ana@andina.pe")), eq("Ana Torres"), link.capture(),
                eq(NOW.plus(Duration.ofMinutes(30))));
        String rawToken = link.getValue().substring("https://app.buildshield.pe/restablecer?token=".length());
        assertThat(stored.getValue().tokenHash()).isEqualTo(OpaqueTokens.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(stored.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void unknown_inactive_or_malformed_email_sends_nothing() {
        when(users.findByEmail(new EmailAddress("inactiva@andina.pe"))).thenReturn(Optional.of(ana(false)));

        service.requestReset("nadie@andina.pe");
        service.requestReset("inactiva@andina.pe");
        service.requestReset("no-es-correo");

        verifyNoInteractions(email, resetTokens);
    }

    @Test
    void confirm_changes_the_password_uses_the_token_and_closes_sessions() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, ORG, OpaqueTokens.hash("enlace"), NOW, Duration.ofMinutes(30));
        when(resetTokens.findByTokenHashForUpdate(OpaqueTokens.hash("enlace"))).thenReturn(Optional.of(token));
        User ana = ana(true);
        when(users.findById(USER_ID)).thenReturn(Optional.of(ana));
        when(encoder.encode("Nueva12345")).thenReturn("$2a$12$new");

        service.confirmReset("enlace", "Nueva12345");

        assertThat(ana.passwordHash()).isEqualTo("$2a$12$new");
        assertThat(token.usedAt()).isEqualTo(NOW);
        verify(users).save(ana);
        verify(resetTokens).save(token);
        verify(refreshTokens).revokeAllActive(USER_ID, NOW);
        assertThat(transactions.commits).hasValue(1);
    }

    @Test
    void used_expired_or_unknown_token_is_rejected_without_changes() {
        PasswordResetToken used = PasswordResetToken.issue(USER_ID, ORG, OpaqueTokens.hash("usado"), NOW, Duration.ofMinutes(30));
        used.use(NOW);
        PasswordResetToken expired = PasswordResetToken.issue(USER_ID, ORG, OpaqueTokens.hash("vencido"),
                NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(30));
        when(resetTokens.findByTokenHashForUpdate(OpaqueTokens.hash("usado"))).thenReturn(Optional.of(used));
        when(resetTokens.findByTokenHashForUpdate(OpaqueTokens.hash("vencido"))).thenReturn(Optional.of(expired));
        when(resetTokens.findByTokenHashForUpdate(OpaqueTokens.hash("desconocido"))).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("h");

        for (String raw : new String[] {"usado", "vencido", "desconocido", " "}) {
            assertThatThrownBy(() -> service.confirmReset(raw, "Nueva12345"))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_RESET_TOKEN");
        }
        verify(users, never()).save(any());
        verify(refreshTokens, never()).revokeAllActive(any(), any());
    }

    @Test
    void token_of_a_deleted_user_is_rejected() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, ORG, OpaqueTokens.hash("t"), NOW, Duration.ofMinutes(30));
        when(resetTokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("h");

        assertThatThrownBy(() -> service.confirmReset("t", "Nueva12345")).isInstanceOf(ValidationException.class);
        assertThat(transactions.rollbacks).hasValue(1);
    }

    @Test
    void weak_new_password_is_rejected_before_looking_at_the_token() {
        assertThatThrownBy(() -> service.confirmReset("enlace", "123"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "WEAK_PASSWORD");
        verifyNoInteractions(resetTokens, users);
    }
}
