package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.MaterialRepository;
import pe.buildshield.core.organization.domain.model.Sku;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El filtro multiempresa hace que la búsqueda y la unicidad del SKU sean por organización. */
@Repository
class JpaMaterialRepository implements MaterialRepository {

    private final SpringDataMaterialRepository jpa;

    JpaMaterialRepository(SpringDataMaterialRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Material> findById(UUID id) {
        return jpa.findById(id).map(MaterialJpaEntity::toDomain);
    }

    @Override
    public List<Material> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(ids).stream().map(MaterialJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Material> findBySku(Sku sku) {
        return jpa.findBySku(sku.value()).map(MaterialJpaEntity::toDomain);
    }

    @Override
    public boolean existsBySku(Sku sku) {
        return jpa.existsBySku(sku.value());
    }

    @Override
    public List<Material> findAll() {
        return jpa.findAllByOrderBySku().stream().map(MaterialJpaEntity::toDomain).toList();
    }

    @Override
    public List<Material> findAllActive() {
        return jpa.findAllByActiveTrueOrderBySku().stream().map(MaterialJpaEntity::toDomain).toList();
    }

    @Override
    public Material save(Material material) {
        if (material.id() == null) {
            return jpa.saveAndFlush(new MaterialJpaEntity(material)).toDomain();
        }
        MaterialJpaEntity entity = Versions.load(jpa, material.id(), material.version(), MaterialJpaEntity.class);
        entity.apply(material);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
