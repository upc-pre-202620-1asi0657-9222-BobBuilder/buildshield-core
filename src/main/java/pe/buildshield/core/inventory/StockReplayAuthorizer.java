package pe.buildshield.core.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pe.buildshield.core.shared.idempotency.ReplayAuthorizer;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.organization.OrganizationContextFacade;
import java.util.UUID;

@Component
public class StockReplayAuthorizer implements ReplayAuthorizer {
    private final OrganizationContextFacade organization;
    private final ObjectMapper json;
    public StockReplayAuthorizer(OrganizationContextFacade organization, ObjectMapper json) {
        this.organization = organization; this.json = json;
    }
    @Override public boolean supports(String path) { return path.equals("/api/v1/stock/entries"); }
    @Override public boolean allowed(String path, byte[] body) {
        var actor = TenantContext.require();
        if (actor.role().equals("ADMINISTRATOR")) return true;
        if (!actor.role().equals("WAREHOUSE_MANAGER")) return false;
        try {
            UUID warehouse = UUID.fromString(json.readTree(body).path("warehouseId").asText());
            return organization.isAssigned(actor.userId(), warehouse);
        } catch (java.io.IOException | IllegalArgumentException ex) { return false; }
    }
}
