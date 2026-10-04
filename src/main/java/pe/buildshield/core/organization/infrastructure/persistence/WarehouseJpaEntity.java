package pe.buildshield.core.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.buildshield.commons.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseType;

@Entity
@Table(schema = "organization", name = "warehouses")
public class WarehouseJpaEntity extends AuditableAbstractAggregateRoot<WarehouseJpaEntity> {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private WarehouseType type;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected WarehouseJpaEntity() {
    }

    WarehouseJpaEntity(Warehouse warehouse) {
        apply(warehouse);
    }

    void apply(Warehouse warehouse) {
        name = warehouse.name();
        type = warehouse.type();
        address = warehouse.address();
        active = warehouse.active();
    }

    Warehouse toDomain() {
        return Warehouse.restore(getId(), name, type, address, active, getVersion());
    }
}
