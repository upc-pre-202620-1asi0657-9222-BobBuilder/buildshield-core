package pe.buildshield.core.dispatch.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.core.dispatch.domain.model.DepartureWeighing;
import pe.buildshield.core.shared.persistence.OrganizationScopedEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tabla dispatch.departure_weighings: un pesaje de salida por despacho; no se modifica. */
@Entity
@Table(schema = "dispatch", name = "departure_weighings")
public class DepartureWeighingJpaEntity extends OrganizationScopedEntity {

    @Id
    private UUID id;

    @Column(name = "gross_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal grossKg;

    @Column(name = "tare_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal tareKg;

    @Column(name = "net_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal netKg;

    @Column(name = "ticket_photo_url", length = 500, updatable = false)
    private String ticketPhotoUrl;

    @Column(name = "weighed_at", nullable = false, updatable = false)
    private Instant weighedAt;

    @Column(name = "weighed_by", nullable = false, updatable = false)
    private UUID weighedBy;

    protected DepartureWeighingJpaEntity() {
    }

    DepartureWeighingJpaEntity(DepartureWeighing weighing) {
        id = UUID.randomUUID();
        grossKg = weighing.grossKg();
        tareKg = weighing.tareKg();
        netKg = weighing.netKg();
        ticketPhotoUrl = weighing.ticketPhotoUrl();
        weighedAt = weighing.weighedAt();
        weighedBy = weighing.weighedBy();
    }

    DepartureWeighing toDomain() {
        return new DepartureWeighing(grossKg, tareKg, netKg, ticketPhotoUrl, weighedAt, weighedBy);
    }
}
