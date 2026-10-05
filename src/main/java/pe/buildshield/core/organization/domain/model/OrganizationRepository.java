package pe.buildshield.core.organization.domain.model;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

    boolean existsByRuc(Ruc ruc);

    Optional<Organization> findById(UUID id);

    void save(Organization organization);
}
