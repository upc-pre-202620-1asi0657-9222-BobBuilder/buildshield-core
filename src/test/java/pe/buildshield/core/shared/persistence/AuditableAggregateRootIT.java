package pe.buildshield.core.shared.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.testapp.ClockTestConfig;
import pe.buildshield.testapp.PostgresIntegrationTest;
import pe.buildshield.testapp.Shipment;
import pe.buildshield.testapp.ShipmentRepository;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.shared.testsupport.MutableClock;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@Import({ClockTestConfig.class, AuditableAggregateRootIT.EventCollector.class})
@Testcontainers(disabledWithoutDocker = true)
class AuditableAggregateRootIT {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final TenantInfo CREATOR = new TenantInfo(ORGANIZATION, UUID.randomUUID(), "WAREHOUSE");
    private static final TenantInfo EDITOR = new TenantInfo(ORGANIZATION, UUID.randomUUID(), "SUPERVISOR");

    @Autowired
    ShipmentRepository shipments;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    List<Object> publishedEvents;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM test_shipments");
        publishedEvents.clear();
    }

    @Test
    void creation_fills_audit_columns_and_version() {
        Shipment saved = TenantContext.callAs(CREATOR, () -> tx.execute(status -> shipments.save(new Shipment())));

        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getOrganizationId()).isEqualTo(ORGANIZATION);
        assertThat(saved.getCreatedBy()).isEqualTo(CREATOR.userId());
        assertThat(saved.getUpdatedBy()).isEqualTo(CREATOR.userId());
        assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(saved.getUpdatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void update_changes_only_modification_columns_and_increments_version() {
        UUID id = TenantContext.callAs(CREATOR, () -> tx.execute(status -> shipments.save(new Shipment()).getId()));
        clock.advance(Duration.ofMinutes(5));

        Shipment updated = TenantContext.callAs(EDITOR, () -> tx.execute(status -> {
            Shipment shipment = shipments.findById(id).orElseThrow();
            shipment.rename("READY");
            return shipments.saveAndFlush(shipment);
        }));

        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getCreatedBy()).isEqualTo(CREATOR.userId());
        assertThat(updated.getCreatedAt()).isEqualTo(ClockTestConfig.START.truncatedTo(ChronoUnit.MICROS));
        assertThat(updated.getUpdatedBy()).isEqualTo(EDITOR.userId());
        assertThat(updated.getUpdatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void concurrent_update_of_the_same_version_fails_with_optimistic_lock() {
        UUID id = TenantContext.callAs(CREATOR, () -> tx.execute(status -> shipments.save(new Shipment()).getId()));
        Shipment firstCopy = TenantContext.callAs(CREATOR, () -> shipments.findById(id).orElseThrow());
        Shipment secondCopy = TenantContext.callAs(EDITOR, () -> shipments.findById(id).orElseThrow());

        TenantContext.runAs(CREATOR, () -> {
            firstCopy.rename("A");
            shipments.save(firstCopy);
        });

        assertThatThrownBy(() -> TenantContext.runAs(EDITOR, () -> {
            secondCopy.rename("B");
            shipments.save(secondCopy);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        String status = jdbc.queryForObject("SELECT status FROM test_shipments WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("A");
    }

    @Test
    void system_mode_audits_with_the_system_user() {
        UUID id = TenantContext.callAs(CREATOR, () -> tx.execute(status -> shipments.save(new Shipment()).getId()));

        Shipment updated = TenantContext.callAsSystem(() -> tx.execute(status -> {
            Shipment shipment = shipments.findById(id).orElseThrow();
            shipment.rename("CLOSED");
            return shipments.saveAndFlush(shipment);
        }));

        assertThat(updated.getUpdatedBy()).isEqualTo(TenantContext.SYSTEM_USER_ID);
        assertThat(updated.getOrganizationId()).isEqualTo(ORGANIZATION);
    }

    @Test
    void registered_domain_events_are_published_on_save_and_cleared() {
        Shipment shipment = new Shipment();
        shipment.dispatch();

        TenantContext.runAs(CREATOR, () -> tx.executeWithoutResult(status -> shipments.save(shipment)));

        assertThat(publishedEvents).containsExactly(new Shipment.ShipmentDispatched(shipment.getId()));
        TenantContext.runAs(CREATOR, () -> tx.executeWithoutResult(status -> shipments.save(shipment)));
        assertThat(publishedEvents).hasSize(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventCollector {

        private final List<Object> events = new CopyOnWriteArrayList<>();

        @Bean
        List<Object> publishedEvents() {
            return events;
        }

        @EventListener
        void on(Shipment.ShipmentDispatched event) {
            events.add(event);
        }
    }
}
