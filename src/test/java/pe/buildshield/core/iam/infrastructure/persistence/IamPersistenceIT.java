package pe.buildshield.core.iam.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.iam.domain.model.EmailAddress;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.iam.domain.model.User;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.support.CoreIntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Registro y usuarios contra PostgreSQL real, con el filtro multiempresa del kernel compartido. */
@CoreIntegrationTest
@Testcontainers(disabledWithoutDocker = true)
class IamPersistenceIT {

    @Autowired
    SignUpService signUp;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM iam.refresh_tokens");
        jdbc.update("DELETE FROM iam.password_reset_tokens");
        jdbc.update("DELETE FROM iam.users");
        jdbc.update("DELETE FROM organization.organizations");
    }

    @Test
    void sign_up_stores_the_organization_and_its_administrator() {
        SignUpService.SignUpResult result = signUp.signUp(command("20123456789", "ana@andina.pe"));

        assertThat(jdbc.queryForObject("SELECT legal_name FROM organization.organizations WHERE id = ?",
                String.class, result.organizationId())).isEqualTo("Constructora 20123456789");
        assertThat(jdbc.queryForMap("SELECT organization_id, role, email, created_by FROM iam.users WHERE id = ?",
                result.administratorId()))
                .containsEntry("organization_id", result.organizationId())
                .containsEntry("role", "ADMINISTRATOR")
                .containsEntry("email", "ana@andina.pe")
                .containsEntry("created_by", TenantContext.SYSTEM_USER_ID);
        String hash = jdbc.queryForObject("SELECT password_hash FROM iam.users WHERE id = ?", String.class,
                result.administratorId());
        assertThat(hash).startsWith("$2a$12$").doesNotContain("Segura123");
    }

    @Test
    void email_registered_in_another_organization_rolls_back_the_whole_sign_up() {
        signUp.signUp(command("20123456789", "ana@andina.pe"));

        assertThatThrownBy(() -> signUp.signUp(command("20999999991", "ANA@andina.pe")))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_REGISTERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization.organizations WHERE ruc = '20999999991'",
                Long.class)).isZero();
    }

    @Test
    void registered_ruc_is_a_409() {
        signUp.signUp(command("20123456789", "ana@andina.pe"));

        assertThatThrownBy(() -> signUp.signUp(command("20123456789", "luis@otra.pe")))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "RUC_ALREADY_REGISTERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.users", Long.class)).isEqualTo(1);
    }

    @Test
    void users_are_filtered_by_organization_but_email_lookup_for_sign_in_sees_all() {
        SignUpService.SignUpResult andina = signUp.signUp(command("20123456789", "ana@andina.pe"));
        SignUpService.SignUpResult otra = signUp.signUp(command("20999999991", "luis@otra.pe"));
        TenantInfo andinaAdmin = new TenantInfo(andina.organizationId(), andina.administratorId(), "ADMINISTRATOR");

        List<String> visibleToAndina = TenantContext.callAs(andinaAdmin, () -> tx.execute(status ->
                users.findAll().stream().map(user -> user.email().value()).toList()));
        boolean luisFoundFromAndina = TenantContext.callAs(andinaAdmin, () -> tx.execute(status ->
                users.findByEmail(new EmailAddress("luis@otra.pe")).isPresent()));
        boolean luisExistsAnywhere = TenantContext.callAs(andinaAdmin, () -> tx.execute(status ->
                users.existsByEmailInAnyOrganization(new EmailAddress("luis@otra.pe"))));
        User luisForSignIn = TenantContext.callAsSystem(() -> tx.execute(status ->
                users.findByEmail(new EmailAddress("luis@otra.pe")).orElseThrow()));

        assertThat(visibleToAndina).containsExactly("ana@andina.pe");
        assertThat(luisFoundFromAndina).isFalse();
        assertThat(luisExistsAnywhere).isTrue();
        assertThat(luisForSignIn.organizationId()).isEqualTo(otra.organizationId());
    }

    @Test
    void stale_version_cannot_overwrite_a_user() {
        SignUpService.SignUpResult andina = signUp.signUp(command("20123456789", "ana@andina.pe"));
        TenantInfo admin = new TenantInfo(andina.organizationId(), andina.administratorId(), "ADMINISTRATOR");
        User loaded = TenantContext.callAs(admin, () -> tx.execute(status -> users.findById(andina.administratorId()).orElseThrow()));
        User stale = User.restore(loaded.id(), loaded.organizationId(), loaded.email(), loaded.fullName(), Role.ADMINISTRATOR,
                loaded.passwordHash(), true, loaded.version());

        TenantContext.runAs(admin, () -> tx.executeWithoutResult(status -> {
            loaded.changePasswordHash("$2a$12$nuevo");
            users.save(loaded);
        }));

        assertThatThrownBy(() -> TenantContext.runAs(admin, () -> tx.executeWithoutResult(status -> {
            stale.changePasswordHash("$2a$12$otro");
            users.save(stale);
        }))).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    void a_user_cannot_be_created_under_another_organization() {
        SignUpService.SignUpResult andina = signUp.signUp(command("20123456789", "ana@andina.pe"));
        TenantInfo admin = new TenantInfo(andina.organizationId(), andina.administratorId(), "ADMINISTRATOR");
        User foreign = User.register(UUID.randomUUID(), new EmailAddress("x@otra.pe"), "X", Role.SITE_MANAGER, "h");

        assertThatThrownBy(() -> TenantContext.runAs(admin, () -> tx.executeWithoutResult(status -> users.save(foreign))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otra organización");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.users WHERE email = 'x@otra.pe'", Long.class)).isZero();
    }

    private static SignUpService.SignUpCommand command(String ruc, String email) {
        return new SignUpService.SignUpCommand(ruc, "Constructora " + ruc, "Administrador", email, "Segura123");
    }
}
