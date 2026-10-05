package pe.buildshield.core.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.buildshield.commons.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.organization.domain.model.Material;
import pe.buildshield.core.organization.domain.model.Sku;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.WasteTolerance;

import java.math.BigDecimal;

@Entity
@Table(schema = "organization", name = "materials")
public class MaterialJpaEntity extends AuditableAbstractAggregateRoot<MaterialJpaEntity> {

    @Column(name = "sku", nullable = false, length = 40, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 10)
    private UnitOfMeasure unit;

    @Column(name = "waste_tolerance_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal wasteTolerancePercent;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected MaterialJpaEntity() {
    }

    MaterialJpaEntity(Material material) {
        sku = material.sku().value();
        apply(material);
    }

    void apply(Material material) {
        name = material.name();
        unit = material.unit();
        wasteTolerancePercent = material.wasteTolerance().percent();
        active = material.active();
    }

    Material toDomain() {
        return Material.restore(getId(), new Sku(sku), name, unit, new WasteTolerance(wasteTolerancePercent), active,
                getVersion());
    }
}
