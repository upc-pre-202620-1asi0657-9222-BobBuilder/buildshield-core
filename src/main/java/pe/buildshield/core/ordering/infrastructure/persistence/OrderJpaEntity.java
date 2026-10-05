package pe.buildshield.core.ordering.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import pe.buildshield.commons.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.ordering.domain.model.Order;
import pe.buildshield.core.ordering.domain.model.OrderLine;
import pe.buildshield.core.ordering.domain.model.OrderStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "ordering", name = "orders")
public class OrderJpaEntity extends AuditableAbstractAggregateRoot<OrderJpaEntity> {

    @Column(name = "worksite_id", nullable = false, updatable = false)
    private UUID worksiteId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "notes", length = 500, updatable = false)
    private String notes;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("sku")
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    OrderJpaEntity(Order order) {
        worksiteId = order.worksiteId();
        warehouseId = order.warehouseId();
        requestedBy = order.requestedBy();
        notes = order.notes();
        placedAt = order.placedAt();
        order.lines().forEach(line -> lines.add(new OrderLineJpaEntity(line)));
        apply(order);
    }

    /** Copia lo que cambia durante la vida del pedido: estado, decisión y cantidades de cada línea. */
    void apply(Order order) {
        status = order.status();
        rejectionReason = order.rejectionReason();
        decidedBy = order.decidedBy();
        decidedAt = order.decidedAt();
        for (OrderLineJpaEntity line : lines) {
            line.apply(order.line(line.materialId()));
        }
    }

    Order toDomain() {
        List<OrderLine> domainLines = lines.stream().map(OrderLineJpaEntity::toDomain).toList();
        return Order.restore(getId(), worksiteId, warehouseId, requestedBy, notes, placedAt, domainLines, status,
                rejectionReason, decidedBy, decidedAt, getVersion());
    }
}
