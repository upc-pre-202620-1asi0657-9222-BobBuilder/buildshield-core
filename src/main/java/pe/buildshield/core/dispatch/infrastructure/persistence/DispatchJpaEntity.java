package pe.buildshield.core.dispatch.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import pe.buildshield.core.dispatch.domain.model.Carrier;
import pe.buildshield.core.dispatch.domain.model.Dispatch;
import pe.buildshield.core.dispatch.domain.model.DispatchStatus;
import pe.buildshield.core.dispatch.domain.model.DispatchType;
import pe.buildshield.core.dispatch.domain.model.ManifestCode;
import pe.buildshield.core.shared.persistence.AuditableAbstractAggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tabla dispatch.dispatches con sus líneas y su pesaje de salida (a lo más uno: la tabla
 * departure_weighings tiene {@code dispatch_id} único). Las colecciones se cargan por lotes para que
 * listar despachos no haga una consulta por despacho.
 */
@Entity
@Table(schema = "dispatch", name = "dispatches")
public class DispatchJpaEntity extends AuditableAbstractAggregateRoot<DispatchJpaEntity> {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "worksite_id", nullable = false, updatable = false)
    private UUID worksiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private DispatchType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private DispatchStatus status;

    @Column(name = "manifest_code", nullable = false, length = 30, updatable = false)
    private String manifestCode;

    @Column(name = "carrier_name", length = 150)
    private String carrierName;

    @Column(name = "carrier_document", length = 20)
    private String carrierDocument;

    @Column(name = "plate", length = 10)
    private String plate;

    @Column(name = "prepared_at", nullable = false, updatable = false)
    private Instant preparedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "dispatch_id", nullable = false, updatable = false)
    @BatchSize(size = 50)
    private List<DispatchLineJpaEntity> lines = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "dispatch_id", nullable = false, updatable = false)
    @BatchSize(size = 50)
    private List<DepartureWeighingJpaEntity> weighings = new ArrayList<>();

    protected DispatchJpaEntity() {
    }

    DispatchJpaEntity(Dispatch dispatch) {
        orderId = dispatch.orderId();
        warehouseId = dispatch.warehouseId();
        worksiteId = dispatch.worksiteId();
        type = dispatch.type();
        manifestCode = dispatch.manifestCode().value();
        preparedAt = dispatch.preparedAt();
        dispatch.lines().forEach(line -> lines.add(new DispatchLineJpaEntity(line)));
        apply(dispatch);
    }

    /** Copia lo que cambia durante la vida del despacho: estado, transportista, pesaje y fechas. */
    void apply(Dispatch dispatch) {
        status = dispatch.status();
        Carrier carrier = dispatch.carrier().orElse(null);
        carrierName = carrier == null ? null : carrier.name();
        carrierDocument = carrier == null ? null : carrier.document();
        plate = carrier == null ? null : carrier.plate();
        departedAt = dispatch.departedAt();
        receivedAt = dispatch.receivedAt();
        if (weighings.isEmpty()) {
            dispatch.departureWeighing().ifPresent(weighing -> weighings.add(new DepartureWeighingJpaEntity(weighing)));
        }
    }

    Dispatch toDomain() {
        Carrier carrier = carrierName == null ? null : new Carrier(carrierName, carrierDocument, plate);
        return Dispatch.restore(getId(), orderId, warehouseId, worksiteId, type, new ManifestCode(manifestCode),
                lines.stream().map(DispatchLineJpaEntity::toDomain).toList(), preparedAt, status, carrier,
                weighings.stream().findFirst().map(DepartureWeighingJpaEntity::toDomain).orElse(null), departedAt,
                receivedAt, getVersion());
    }
}
