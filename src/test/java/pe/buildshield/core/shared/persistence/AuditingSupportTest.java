package pe.buildshield.core.shared.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.testapp.Shipment;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditingSupportTest {

    private final TenantAuditorAware auditor = new TenantAuditorAware();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void auditor_is_the_user_of_the_token() {
        UUID userId = UUID.randomUUID();
        TenantContext.set(new TenantInfo(UUID.randomUUID(), userId, "SUPERVISOR"));

        assertThat(auditor.getCurrentAuditor()).contains(userId);
    }

    @Test
    void auditor_is_the_system_user_in_system_mode() {
        assertThat(TenantContext.callAsSystem(auditor::getCurrentAuditor)).contains(TenantContext.SYSTEM_USER_ID);
    }

    @Test
    void there_is_no_auditor_without_context() {
        assertThat(auditor.getCurrentAuditor()).isEmpty();
    }

    @Test
    void new_aggregate_has_an_id_and_no_version_so_it_is_persisted_as_new() {
        Shipment shipment = new Shipment();

        assertThat(shipment.getId()).isNotNull();
        assertThat(shipment.getVersion()).isNull();
        assertThat(shipment.getCreatedAt()).isNull();
    }

    @Test
    void domain_events_are_registered_and_cleared() {
        Shipment shipment = new Shipment();
        shipment.dispatch();

        assertThat(shipment.domainEvents()).containsExactly(new Shipment.ShipmentDispatched(shipment.getId()));
        shipment.clearDomainEvents();
        assertThat(shipment.domainEvents()).isEmpty();
    }
}
