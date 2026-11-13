package pe.buildshield.core.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.shared.idempotency.ReplayAuthorizer;
import pe.buildshield.core.shared.tenant.TenantContext;

import java.util.UUID;

/**
 * Antes de reproducir la respuesta guardada de crear o despachar un despacho, revisa que el actor siga
 * siendo administrador o encargado asignado al almacén de origen (el de la respuesta).
 */
@Component
public class DispatchReplayAuthorizer implements ReplayAuthorizer {
    private final OrganizationContextFacade organization;
    private final ObjectMapper json;

    public DispatchReplayAuthorizer(OrganizationContextFacade organization, ObjectMapper json) {
        this.organization = organization;
        this.json = json;
    }

    @Override
    public boolean supports(String path) {
        return path.equals("/api/v1/dispatches") || path.matches("/api/v1/dispatches/[^/]+/depart");
    }

    @Override
    public boolean allowed(String path, byte[] body) {
        var actor = TenantContext.require();
        if (actor.role().equals("ADMINISTRATOR")) {
            return true;
        }
        if (!actor.role().equals("WAREHOUSE_MANAGER")) {
            return false;
        }
        try {
            UUID warehouse = UUID.fromString(json.readTree(body).path("warehouseId").asText());
            return organization.isAssigned(actor.userId(), warehouse);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            return false;
        }
    }
}
