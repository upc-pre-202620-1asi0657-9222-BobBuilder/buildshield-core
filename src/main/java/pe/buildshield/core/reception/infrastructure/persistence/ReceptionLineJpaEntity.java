package pe.buildshield.core.reception.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.core.reception.domain.model.ReceptionLine;
import pe.buildshield.core.shared.persistence.OrganizationScopedEntity;

import java.math.BigDecimal;
import java.util.UUID;

/** Tabla reception.reception_lines; lleva organization_id como toda tabla de negocio. */
@Entity
@Table(schema = "reception", name = "reception_lines")
public class ReceptionLineJpaEntity extends OrganizationScopedEntity {

    @Id
    private UUID id;

    @Column(name = "dispatch_line_id", nullable = false, updatable = false)
    private UUID dispatchLineId;

    @Column(name = "order_line_id", nullable = false, updatable = false)
    private UUID orderLineId;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "dispatched_qty", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal dispatchedQty;

    @Column(name = "received_qty", precision = 14, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "shrinkage_pct", precision = 6, scale = 2)
    private BigDecimal shrinkagePct;

    protected ReceptionLineJpaEntity() {
    }

    ReceptionLineJpaEntity(ReceptionLine line) {
        id = UUID.randomUUID();
        dispatchLineId = line.dispatchLineId();
        orderLineId = line.orderLineId();
        materialId = line.materialId();
        dispatchedQty = line.dispatchedQty();
        apply(line);
    }

    void apply(ReceptionLine line) {
        receivedQty = line.receivedQty();
        shrinkagePct = line.shrinkagePercent();
    }

    UUID id() {
        return id;
    }

    ReceptionLine toDomain() {
        return ReceptionLine.restore(id, dispatchLineId, orderLineId, materialId, dispatchedQty, receivedQty, shrinkagePct);
    }
}
