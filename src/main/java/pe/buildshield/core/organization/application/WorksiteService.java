package pe.buildshield.core.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.organization.domain.model.Location;
import pe.buildshield.core.organization.domain.model.Worksite;
import pe.buildshield.core.organization.domain.model.WorksiteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US14: registro y mantenimiento de obras, dentro de la organización del contexto. */
@Service
public class WorksiteService {

    private final WorksiteRepository worksites;
    private final SiteVisibility visibility;

    public WorksiteService(WorksiteRepository worksites, SiteVisibility visibility) {
        this.worksites = worksites;
        this.visibility = visibility;
    }

    @Transactional
    public WorksiteView register(RegisterWorksite command) {
        Worksite worksite = Worksite.register(command.name(), command.location(), command.startDate(), command.endDate());
        return WorksiteView.of(worksites.save(worksite));
    }

    /** 404 si no existe, es de otra organización o quien consulta no tiene acceso a ella. */
    @Transactional(readOnly = true)
    public WorksiteView get(UUID id) {
        return WorksiteView.of(visibleWorksite(id));
    }

    @Transactional(readOnly = true)
    public List<WorksiteView> list() {
        return visibility.visibleWorksites().stream().map(WorksiteView::of).toList();
    }

    @Transactional
    public WorksiteView update(UUID id, UpdateWorksite command) {
        Worksite worksite = worksites.findById(id).orElseThrow(() -> notFound(id));
        worksite.update(command.name(), command.mergedLocation(worksite.location()), command.startDate(),
                command.endDate());
        return WorksiteView.of(worksites.save(worksite));
    }

    private Worksite visibleWorksite(UUID id) {
        return worksites.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id));
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("WORKSITE_NOT_FOUND", "La obra " + id + " no existe");
    }

    public record RegisterWorksite(String name, Location location, LocalDate startDate, LocalDate endDate) {
    }

    /** Campos nulos: no cambian. Las coordenadas se cambian juntas. */
    public record UpdateWorksite(String name, String address, String district, String city, Double latitude,
            Double longitude, LocalDate startDate, LocalDate endDate) {

        Location mergedLocation(Location current) {
            if (address == null && district == null && city == null && latitude == null && longitude == null) {
                return null;
            }
            boolean newCoordinates = latitude != null || longitude != null;
            return new Location(
                    address != null ? address : current.address(),
                    district != null ? district : current.district(),
                    city != null ? city : current.city(),
                    newCoordinates ? latitude : current.latitude(),
                    newCoordinates ? longitude : current.longitude());
        }
    }

    public record WorksiteView(UUID id, String name, Location location, LocalDate startDate, LocalDate endDate) {

        static WorksiteView of(Worksite worksite) {
            return new WorksiteView(worksite.id(), worksite.name(), worksite.location(), worksite.startDate(),
                    worksite.endDate());
        }
    }
}
