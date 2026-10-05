package pe.buildshield.core.ordering;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pe.buildshield.core.shared.idempotency.ReplayAuthorizer;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.organization.OrganizationContextFacade;
import java.util.UUID;

@Component
public class OrderReplayAuthorizer implements ReplayAuthorizer {
    private final OrganizationContextFacade organization;
    private final ObjectMapper json;
    public OrderReplayAuthorizer(OrganizationContextFacade organization, ObjectMapper json) {
        this.organization = organization; this.json = json;
    }
    @Override public boolean supports(String path) { return path.equals("/api/v1/orders") || path.matches("/api/v1/orders/[^/]+/(approve|reject)"); }
    @Override public boolean allowed(String path, byte[] body) {
        var actor = TenantContext.require();
        boolean creating = path.equals("/api/v1/orders");
        if (!creating && actor.role().equals("ADMINISTRATOR")) return true;
        if (!actor.role().equals(creating ? "SITE_MANAGER" : "WAREHOUSE_MANAGER")) return false;
        try {
            UUID site = UUID.fromString(json.readTree(body).path(creating ? "worksiteId" : "warehouseId").asText());
            return organization.isAssigned(actor.userId(), site);
        } catch (java.io.IOException | IllegalArgumentException ex) { return false; }
    }
}
