package pe.buildshield.core.shared.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.testapp.Note;
import pe.buildshield.testapp.NoteRepository;
import pe.buildshield.testapp.PostgresIntegrationTest;
import pe.buildshield.core.shared.tenant.MissingTenantContextException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Testcontainers(disabledWithoutDocker = true)
class OrganizationScopedEntityIT {

    private static final TenantInfo ALPHA = new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "SUPERVISOR");
    private static final TenantInfo BETA = new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "SUPERVISOR");

    @Autowired
    NoteRepository notes;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private Note alphaNote;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM test_notes");
        alphaNote = TenantContext.callAs(ALPHA, () -> tx.execute(status -> notes.save(new Note("cemento alpha"))));
    }

    @Test
    void insert_takes_the_organization_from_the_context() {
        UUID stored = jdbc.queryForObject("SELECT organization_id FROM test_notes WHERE id = ?", UUID.class, alphaNote.getId());

        assertThat(stored).isEqualTo(ALPHA.organizationId());
    }

    @Test
    void another_organization_sees_nothing_with_find_all_find_by_id_or_jpql() {
        TenantContext.runAs(BETA, () -> tx.executeWithoutResult(status -> {
            assertThat(notes.findAll()).isEmpty();
            assertThat(notes.findById(alphaNote.getId())).isEmpty();
            assertThat(notes.existsById(alphaNote.getId())).isFalse();
            assertThat(notes.countByTextJpql("cemento alpha")).isZero();
            assertThat(notes.count()).isZero();
        }));
    }

    @Test
    void the_owner_organization_sees_its_data() {
        TenantContext.runAs(ALPHA, () -> tx.executeWithoutResult(status -> {
            assertThat(notes.findById(alphaNote.getId())).isPresent();
            assertThat(notes.countByTextJpql("cemento alpha")).isEqualTo(1);
        }));
    }

    @Test
    void each_organization_only_sees_its_own_rows() {
        TenantContext.runAs(BETA, () -> tx.executeWithoutResult(status -> notes.save(new Note("fierro beta"))));

        List<String> alphaTexts = TenantContext.callAs(ALPHA, () ->
                tx.execute(status -> notes.findAll().stream().map(Note::getText).toList()));
        List<String> betaTexts = TenantContext.callAs(BETA, () ->
                tx.execute(status -> notes.findAll().stream().map(Note::getText).toList()));

        assertThat(alphaTexts).containsExactly("cemento alpha");
        assertThat(betaTexts).containsExactly("fierro beta");
    }

    @Test
    void another_organization_cannot_delete_foreign_rows() {
        TenantContext.runAs(BETA, () -> tx.executeWithoutResult(status -> notes.deleteById(alphaNote.getId())));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_notes", Long.class)).isEqualTo(1);
    }

    @Test
    void without_context_any_access_fails_safely() {
        assertThatThrownBy(() -> tx.execute(status -> notes.findAll()))
                .satisfies(OrganizationScopedEntityIT::causedByMissingTenant);
        assertThatThrownBy(() -> notes.findAll())
                .satisfies(OrganizationScopedEntityIT::causedByMissingTenant);
        assertThatThrownBy(() -> tx.execute(status -> notes.save(new Note("sin organización"))))
                .satisfies(OrganizationScopedEntityIT::causedByMissingTenant);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_notes", Long.class)).isEqualTo(1);
    }

    private static void causedByMissingTenant(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof MissingTenantContextException) {
                return;
            }
        }
        throw new AssertionError("Se esperaba MissingTenantContextException en la cadena de causas", thrown);
    }

    @Test
    void system_mode_sees_every_organization() {
        TenantContext.runAs(BETA, () -> tx.executeWithoutResult(status -> notes.save(new Note("fierro beta"))));

        long total = TenantContext.callAsSystem(() -> tx.execute(status -> notes.count()));

        assertThat(total).isEqualTo(2);
    }
}
