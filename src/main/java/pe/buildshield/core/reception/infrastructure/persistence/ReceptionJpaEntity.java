package pe.buildshield.core.reception.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import pe.buildshield.core.reception.domain.model.Reception;
import pe.buildshield.core.reception.domain.model.ReceptionLine;
import pe.buildshield.core.reception.domain.model.ReceptionStatus;
import pe.buildshield.core.shared.persistence.AuditableAbstractAggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tabla reception.receptions con sus líneas (reception.reception_lines). */
@Entity
@Table(schema = "reception", name = "receptions")
public class ReceptionJpaEntity extends AuditableAbstractAggregateRoot<ReceptionJpaEntity> {

    @Column(name = "dispatch_id", nullable = false, updatable = false)
    private UUID dispatchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "worksite_id", nullable = false, updatable = false)
    private UUID worksiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ReceptionStatus status;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "reception_id", nullable = false, updatable = false)
    private List<ReceptionLineJpaEntity> lines = new ArrayList<>();

    protected ReceptionJpaEntity() {
    }

    ReceptionJpaEntity(Reception reception) {
        dispatchId = reception.dispatchId();
        orderId = reception.orderId();
        warehouseId = reception.warehouseId();
        worksiteId = reception.worksiteId();
        reception.lines().forEach(line -> lines.add(new ReceptionLineJpaEntity(line)));
        status = reception.status();
    }

    /** Copia lo que cambia: estado, conformidad y lo recibido de cada línea. */
    void apply(Reception reception) {
        status = reception.status();
        confirmedBy = reception.confirmedBy();
        confirmedAt = reception.confirmedAt();
        for (ReceptionLineJpaEntity line : lines) {
            line.apply(reception.line(line.id()));
        }
    }

    Reception toDomain() {
        List<ReceptionLine> domainLines = lines.stream().map(ReceptionLineJpaEntity::toDomain).toList();
        return Reception.restore(getId(), dispatchId, orderId, warehouseId, worksiteId, domainLines, status,
                confirmedBy, confirmedAt, getVersion());
    }
}
