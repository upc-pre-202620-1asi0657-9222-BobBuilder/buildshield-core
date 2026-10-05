package pe.buildshield.core.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.buildshield.core.shared.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.Worksite;

import java.time.LocalDate;

@Entity
@Table(schema = "organization", name = "worksites")
public class WorksiteJpaEntity extends AuditableAbstractAggregateRoot<WorksiteJpaEntity> {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected WorksiteJpaEntity() {
    }

    WorksiteJpaEntity(Worksite worksite) {
        apply(worksite);
    }

    void apply(Worksite worksite) {
        name = worksite.name();
        address = worksite.location().address();
        district = worksite.location().district();
        city = worksite.location().city();
        latitude = worksite.location().latitude();
        longitude = worksite.location().longitude();
        startDate = worksite.startDate();
        endDate = worksite.endDate();
    }

    Worksite toDomain() {
        return Worksite.restore(getId(), name, new Location(address, district, city, latitude, longitude), startDate,
                endDate, getVersion());
    }
}
