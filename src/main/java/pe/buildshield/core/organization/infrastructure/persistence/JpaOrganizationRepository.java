package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.core.organization.domain.model.Organization;
import pe.buildshield.core.organization.domain.model.OrganizationRepository;
import pe.buildshield.core.organization.domain.model.Ruc;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaOrganizationRepository implements OrganizationRepository {

    private final SpringDataOrganizationRepository jpa;

    JpaOrganizationRepository(SpringDataOrganizationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByRuc(Ruc ruc) {
        return jpa.existsByRuc(ruc.value());
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return jpa.findById(id).map(e -> Organization.restore(e.getId(), new Ruc(e.getRuc()), e.getLegalName()));
    }

    @Override
    public void save(Organization organization) {
        // saveAndFlush: la restricción UNIQUE del RUC se detecta dentro de la transacción.
        jpa.saveAndFlush(new OrganizationJpaEntity(organization.id(), organization.ruc().value(), organization.legalName()));
    }
}
