package pe.buildshield.core.reception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.shared.idempotency.ReplayAuthorizer;
import pe.buildshield.core.shared.tenant.TenantContext;

import java.util.UUID;

/**
 * Antes de reproducir la respuesta guardada de una conformidad, revisa que el actor siga siendo
 * administrador o encargado asignado a la obra de destino (la de la respuesta).
 */
@Component
public class ReceptionReplayAuthorizer implements ReplayAuthorizer {
    private final OrganizationContextFacade organization;
    private final ObjectMapper json;

    public ReceptionReplayAuthorizer(OrganizationContextFacade organization, ObjectMapper json) {
        this.organization = organization;
        this.json = json;
    }

    @Override
    public boolean supports(String path) {
        return path.matches("/api/v1/receptions/[^/]+/confirm");
    }

    @Override
    public boolean allowed(String path, byte[] body) {
        var actor = TenantContext.require();
        if (actor.role().equals("ADMINISTRATOR")) {
            return true;
        }
        if (!actor.role().equals("SITE_MANAGER")) {
            return false;
        }
        try {
            UUID worksite = UUID.fromString(json.readTree(body).path("worksiteId").asText());
            return organization.isAssigned(actor.userId(), worksite);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            return false;
        }
    }
}
