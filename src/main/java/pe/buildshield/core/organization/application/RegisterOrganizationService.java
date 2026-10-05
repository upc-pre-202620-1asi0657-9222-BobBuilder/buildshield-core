package pe.buildshield.core.organization.application;

import pe.buildshield.core.audit.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.organization.domain.model.Organization;
import pe.buildshield.core.organization.domain.model.OrganizationRepository;
import pe.buildshield.core.organization.domain.model.Ruc;

import java.util.UUID;

@Service
public class RegisterOrganizationService {

    public static final String RUC_ALREADY_REGISTERED = "RUC_ALREADY_REGISTERED";

    private final AuditTrail audit;
    private final OrganizationRepository organizations;

    public RegisterOrganizationService(OrganizationRepository organizations, AuditTrail audit) {
        this.organizations = organizations;
        this.audit = audit;
    }

    /** Se ejecuta dentro de la transacción de quien registra (por ejemplo, el alta en iam). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Organization register(UUID organizationId, String ruc, String legalName) {
        Organization organization = Organization.register(organizationId, new Ruc(ruc), legalName);
        if (organizations.existsByRuc(organization.ruc())) {
            throw new ConflictException(RUC_ALREADY_REGISTERED, "Ya existe una organización con el RUC " + ruc);
        }
        organizations.save(organization);
        audit.record("ORGANIZATION_CREATED", "ORGANIZATION", organizationId,
                java.util.Map.of("ruc", organization.ruc().value(), "legalName", organization.legalName()));
        return organization;
    }
}
