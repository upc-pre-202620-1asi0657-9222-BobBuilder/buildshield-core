package pe.buildshield.core.organization.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.organization.StaffDirectory;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.StaffAssignment;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.WasteTolerance;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;
import pe.buildshield.core.support.CoreIntegrationTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Restricciones de la base que el servicio normalmente esconde (altas simultáneas) y el puerto
 * StaffDirectory implementado por iam, contra PostgreSQL real.
 */
@CoreIntegrationTest
@Testcontainers(disabledWithoutDocker = true)
class OrganizationPersistenceIT {

    @Autowired
    SignUpService signUp;

    @Autowired
    MaterialRepository materials;

    @Autowired
    WorksiteRepository worksites;

    @Autowired
    StaffAssignmentRepository assignments;

    @Autowired
    StaffDirectory staffDirectory;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private TenantInfo andina;
    private TenantInfo otra;

    @BeforeEach
    void twoOrganizations() {
        jdbc.execute("""
                TRUNCATE organization.staff_assignments, organization.materials, organization.warehouses,
                         organization.worksites, iam.refresh_tokens, iam.password_reset_tokens, iam.users,
                         organization.organizations CASCADE""");
        SignUpService.SignUpResult a = signUp.signUp(new SignUpService.SignUpCommand(
                "20123456789", "Andina", "Ana", "ana@andina.pe", "Segura123"));
        SignUpService.SignUpResult b = signUp.signUp(new SignUpService.SignUpCommand(
                "20999999991", "Otra", "Luis", "luis@otra.pe", "Segura123"));
        andina = new TenantInfo(a.organizationId(), a.administratorId(), "ADMINISTRATOR");
        otra = new TenantInfo(b.organizationId(), b.administratorId(), "ADMINISTRATOR");
    }

    @Test
    void sku_is_unique_per_organization_in_the_database() {
        in(andina, () -> materials.save(cement()));
        in(otra, () -> materials.save(cement()));

        assertThatThrownBy(() -> in(andina, () -> materials.save(cement())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_materials_organization_sku");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization.materials WHERE sku = 'CEM-001'", Long.class))
                .isEqualTo(2);
    }

    @Test
    void only_one_active_assignment_per_user_and_site_and_history_is_kept() {
        Worksite torre = in(andina, () -> worksites.save(Worksite.register("Torre",
                new Location("Av. 1", "Lince", "Lima", null, null), LocalDate.of(2026, 11, 1), null)));
        UUID jorge = UUID.randomUUID();
        StaffAssignment first = in(andina, () -> assignments.save(
                StaffAssignment.assign(jorge, "SITE_MANAGER", SiteType.WORKSITE, torre.id(), Instant.now())));

        assertThatThrownBy(() -> in(andina, () -> assignments.save(
                StaffAssignment.assign(jorge, "SITE_MANAGER", SiteType.WORKSITE, torre.id(), Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_staff_assignments_active_worksite");

        in(andina, () -> {
            first.end(Instant.now());
            return assignments.save(first);
        });
        in(andina, () -> assignments.save(
                StaffAssignment.assign(jorge, "SITE_MANAGER", SiteType.WORKSITE, torre.id(), Instant.now())));

        assertThat(in(andina, () -> assignments.findByUserId(jorge))).hasSize(2);
        assertThat(in(andina, () -> assignments.activeSiteIds(jorge, SiteType.WORKSITE))).containsExactly(torre.id());
        assertThat(in(andina, () -> assignments.activeSiteIds(jorge, SiteType.WAREHOUSE))).isEmpty();
    }

    @Test
    void staff_directory_finds_users_of_the_context_organization_only() {
        UUID ana = andina.userId();

        assertThat(in(andina, () -> staffDirectory.findMember(ana)))
                .hasValue(new StaffDirectory.StaffMember(ana, "ADMINISTRATOR", true));
        assertThat(in(otra, () -> staffDirectory.findMember(ana))).isEmpty();
    }

    @Test
    void materials_and_worksites_are_filtered_by_organization() {
        Material cement = in(andina, () -> materials.save(cement()));

        assertThat(in(otra, () -> materials.findById(cement.id()))).isEmpty();
        assertThat(in(otra, () -> materials.findBySku(new Sku("CEM-001")))).isEmpty();
        assertThat(in(andina, () -> materials.findBySku(new Sku("cem-001")))).isPresent();
    }

    private <T> T in(TenantInfo tenant, Supplier<T> work) {
        return TenantContext.callAs(tenant, () -> tx.execute(status -> work.get()));
    }

    private static Material cement() {
        return Material.register(new Sku("CEM-001"), "Cemento", UnitOfMeasure.BAG, new WasteTolerance(new BigDecimal("2.5")));
    }
}
