package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaWarehouseRepository implements WarehouseRepository {

    private final SpringDataWarehouseRepository jpa;

    JpaWarehouseRepository(SpringDataWarehouseRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
        return jpa.findById(id).map(WarehouseJpaEntity::toDomain);
    }

    @Override
    public List<Warehouse> findAll() {
        return jpa.findAll(Sort.by("name")).stream().map(WarehouseJpaEntity::toDomain).toList();
    }

    @Override
    public List<Warehouse> findAllActive() {
        return jpa.findAllByActiveTrueOrderByName().stream().map(WarehouseJpaEntity::toDomain).toList();
    }

    @Override
    public List<Warehouse> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(WarehouseJpaEntity::toDomain)
                .sorted(Comparator.comparing(Warehouse::name)).toList();
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        if (warehouse.id() == null) {
            return jpa.saveAndFlush(new WarehouseJpaEntity(warehouse)).toDomain();
        }
        WarehouseJpaEntity entity = Versions.load(jpa, warehouse.id(), warehouse.version(), WarehouseJpaEntity.class);
        entity.apply(warehouse);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
