package pe.buildshield.core.ordering.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.core.shared.persistence.OrganizationScopedEntity;
import pe.buildshield.core.ordering.domain.model.OrderLine;

import java.math.BigDecimal;
import java.util.UUID;

/** Tabla ordering.order_lines; lleva organization_id como toda tabla de negocio. */
@Entity
@Table(schema = "ordering", name = "order_lines")
public class OrderLineJpaEntity extends OrganizationScopedEntity {

    @Id
    private UUID id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "sku", nullable = false, length = 40, updatable = false)
    private String sku;

    @Column(name = "unit", nullable = false, length = 10, updatable = false)
    private String unit;

    @Column(name = "requested_qty", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal requestedQty;

    @Column(name = "dispatched_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal dispatchedQty;

    @Column(name = "cancelled_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal cancelledQty;

    @Column(name = "received_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal receivedQty;

    protected OrderLineJpaEntity() {
    }

    OrderLineJpaEntity(OrderLine line) {
        id = UUID.randomUUID();
        materialId = line.materialId();
        sku = line.sku();
        unit = line.unit();
        requestedQty = line.requested();
        apply(line);
    }

    void apply(OrderLine line) {
        dispatchedQty = line.dispatched();
        cancelledQty = line.cancelled();
        receivedQty = line.received();
    }

    UUID materialId() {
        return materialId;
    }

    OrderLine toDomain() {
        return OrderLine.restore(id, materialId, sku, unit, requestedQty, dispatchedQty, cancelledQty, receivedQty);
    }
}
