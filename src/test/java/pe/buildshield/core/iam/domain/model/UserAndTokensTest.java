package pe.buildshield.core.iam.domain.model;

import org.junit.jupiter.api.Test;
import pe.buildshield.commons.error.ValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAndTokensTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final EmailAddress EMAIL = new EmailAddress("ana@andina.pe");

    @Test
    void new_user_is_active_without_id() {
        User user = User.register(ORG, EMAIL, "  Ana Torres ", Role.ADMINISTRATOR, "$2a$hash");

        assertThat(user.id()).isNull();
        assertThat(user.version()).isNull();
        assertThat(user.fullName()).isEqualTo("Ana Torres");
        assertThat(user.canSignIn()).isTrue();
        assertThat(user.organizationId()).isEqualTo(ORG);
        assertThat(user.email()).isEqualTo(EMAIL);
        assertThat(user.role()).isEqualTo(Role.ADMINISTRATOR);
    }

    @Test
    void full_name_is_required_and_bounded() {
        assertThatThrownBy(() -> User.register(ORG, EMAIL, " ", Role.ADMINISTRATOR, "h"))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "INVALID_FULL_NAME");
        assertThatThrownBy(() -> User.register(ORG, EMAIL, null, Role.ADMINISTRATOR, "h"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> User.register(ORG, EMAIL, "x".repeat(151), Role.ADMINISTRATOR, "h"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void password_hash_is_required_and_can_change() {
        assertThatThrownBy(() -> User.register(ORG, EMAIL, "Ana", Role.ADMINISTRATOR, " "))
                .isInstanceOf(IllegalArgumentException.class);

        User user = User.restore(USER, ORG, EMAIL, "Ana", Role.SITE_MANAGER, "old", false, 3L);
        user.changePasswordHash("new");

        assertThat(user.passwordHash()).isEqualTo("new");
        assertThat(user.canSignIn()).isFalse();
        assertThat(user.active()).isFalse();
        assertThat(user.version()).isEqualTo(3L);
        assertThat(user.id()).isEqualTo(USER);
        assertThatThrownBy(() -> user.changePasswordHash(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refresh_token_is_active_until_it_expires() {
        RefreshToken token = RefreshToken.issue(USER, ORG, "hash", NOW, Duration.ofDays(7));

        assertThat(token.isActive(NOW.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue();
        assertThat(token.isActive(NOW.plus(Duration.ofDays(7)))).isFalse();
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.issuedAt()).isEqualTo(NOW);
        assertThat(token.userId()).isEqualTo(USER);
        assertThat(token.organizationId()).isEqualTo(ORG);
        assertThat(token.tokenHash()).isEqualTo("hash");
    }

    @Test
    void rotation_revokes_the_token_and_links_the_next_one() {
        RefreshToken token = RefreshToken.issue(USER, ORG, "hash-1", NOW, Duration.ofDays(7));
        Instant later = NOW.plus(Duration.ofHours(1));

        RefreshToken next = token.rotate("hash-2", later, Duration.ofDays(7));

        assertThat(token.isActive(later)).isFalse();
        assertThat(token.revokedAt()).isEqualTo(later);
        assertThat(token.replacedBy()).isEqualTo(next.id());
        assertThat(next.isActive(later)).isTrue();
        assertThat(next.tokenHash()).isEqualTo("hash-2");
        assertThat(next.expiresAt()).isEqualTo(later.plus(Duration.ofDays(7)));
    }

    @Test
    void revoked_or_expired_token_cannot_rotate() {
        RefreshToken revoked = RefreshToken.issue(USER, ORG, "h", NOW, Duration.ofDays(7));
        revoked.revoke(NOW);
        RefreshToken expired = RefreshToken.issue(USER, ORG, "h", NOW, Duration.ofDays(7));

        assertThatThrownBy(() -> revoked.rotate("x", NOW, Duration.ofDays(7)))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> expired.rotate("x", NOW.plus(Duration.ofDays(8)), Duration.ofDays(7)))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void revoking_twice_keeps_the_first_date() {
        RefreshToken token = RefreshToken.issue(USER, ORG, "h", NOW, Duration.ofDays(7));

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(60));

        assertThat(token.revokedAt()).isEqualTo(NOW);
    }

    @Test
    void restored_refresh_token_keeps_its_state() {
        UUID id = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        RefreshToken token = RefreshToken.restore(id, USER, ORG, "h", NOW, NOW.plusSeconds(10), NOW, next);

        assertThat(token.id()).isEqualTo(id);
        assertThat(token.replacedBy()).isEqualTo(next);
        assertThat(token.isActive(NOW)).isFalse();
    }

    @Test
    void reset_token_is_used_once_before_it_expires() {
        PasswordResetToken token = PasswordResetToken.issue(USER, ORG, "h", NOW, Duration.ofMinutes(30));

        assertThat(token.isUsable(NOW.plus(Duration.ofMinutes(29)))).isTrue();
        token.use(NOW.plus(Duration.ofMinutes(29)));

        assertThat(token.usedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(29)));
        assertThatThrownBy(() -> token.use(NOW.plus(Duration.ofMinutes(29))))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", PasswordResetToken.INVALID_RESET_TOKEN);
    }

    @Test
    void expired_reset_token_cannot_be_used() {
        PasswordResetToken token = PasswordResetToken.issue(USER, ORG, "h", NOW, Duration.ofMinutes(30));

        assertThat(token.isUsable(NOW.plus(Duration.ofMinutes(30)))).isFalse();
        assertThatThrownBy(() -> token.use(NOW.plus(Duration.ofMinutes(31)))).isInstanceOf(ValidationException.class);
    }

    @Test
    void restored_reset_token_keeps_its_state() {
        UUID id = UUID.randomUUID();
        PasswordResetToken token = PasswordResetToken.restore(id, USER, ORG, "h", NOW, null);

        assertThat(token.id()).isEqualTo(id);
        assertThat(token.userId()).isEqualTo(USER);
        assertThat(token.organizationId()).isEqualTo(ORG);
        assertThat(token.tokenHash()).isEqualTo("h");
        assertThat(token.expiresAt()).isEqualTo(NOW);
    }
}
