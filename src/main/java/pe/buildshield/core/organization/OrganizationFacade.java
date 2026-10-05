package pe.buildshield.core.organization;

import org.springframework.stereotype.Component;
import pe.buildshield.core.organization.application.RegisterOrganizationService;

import java.util.UUID;

/**
 * Fachada pública del módulo organization: es lo único que otros módulos del Core pueden usar
 * (lo verifica {@code ModuleBoundariesTest}).
 */
@Component
public class OrganizationFacade {

    private final RegisterOrganizationService registerOrganization;

    public OrganizationFacade(RegisterOrganizationService registerOrganization) {
        this.registerOrganization = registerOrganization;
    }

    /**
     * Registra la organización dentro de la transacción en curso.
     *
     * @throws pe.buildshield.commons.error.ValidationException RUC o razón social inválidos
     * @throws pe.buildshield.commons.error.ConflictException   RUC ya registrado ({@code RUC_ALREADY_REGISTERED})
     */
    public void registerOrganization(UUID organizationId, String ruc, String legalName) {
        registerOrganization.register(organizationId, ruc, legalName);
    }
}
