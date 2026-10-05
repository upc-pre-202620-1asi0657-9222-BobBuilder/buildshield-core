package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaWorksiteRepository implements WorksiteRepository {

    private static final Sort BY_NAME = Sort.by("name");

    private final SpringDataWorksiteRepository jpa;

    JpaWorksiteRepository(SpringDataWorksiteRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Worksite> findById(UUID id) {
        return jpa.findById(id).map(WorksiteJpaEntity::toDomain);
    }

    @Override
    public List<Worksite> findAll() {
        return jpa.findAll(BY_NAME).stream().map(WorksiteJpaEntity::toDomain).toList();
    }

    @Override
    public List<Worksite> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(WorksiteJpaEntity::toDomain)
                .sorted(Comparator.comparing(Worksite::name)).toList();
    }

    @Override
    public Worksite save(Worksite worksite) {
        if (worksite.id() == null) {
            return jpa.saveAndFlush(new WorksiteJpaEntity(worksite)).toDomain();
        }
        WorksiteJpaEntity entity = Versions.load(jpa, worksite.id(), worksite.version(), WorksiteJpaEntity.class);
        entity.apply(worksite);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
