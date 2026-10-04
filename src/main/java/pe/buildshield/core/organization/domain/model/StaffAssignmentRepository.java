package pe.buildshield.core.organization.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Asignaciones de la organización del contexto. */
public interface StaffAssignmentRepository {

    Optional<StaffAssignment> findById(UUID id);

    List<StaffAssignment> findAll();

    List<StaffAssignment> findByUserId(UUID userId);

    boolean existsActive(UUID userId, UUID siteId);

    /** Lugares de ese tipo a los que el usuario está asignado hoy. */
    Set<UUID> activeSiteIds(UUID userId, SiteType siteType);

    StaffAssignment save(StaffAssignment assignment);
}
