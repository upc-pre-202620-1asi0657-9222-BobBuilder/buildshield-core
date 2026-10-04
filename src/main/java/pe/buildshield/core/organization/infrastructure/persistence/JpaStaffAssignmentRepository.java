package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.core.organization.domain.model.SiteType;
import pe.buildshield.core.organization.domain.model.StaffAssignment;
import pe.buildshield.core.organization.domain.model.StaffAssignmentRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JpaStaffAssignmentRepository implements StaffAssignmentRepository {

    private final SpringDataStaffAssignmentRepository jpa;

    JpaStaffAssignmentRepository(SpringDataStaffAssignmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<StaffAssignment> findById(UUID id) {
        return jpa.findById(id).map(StaffAssignmentJpaEntity::toDomain);
    }

    @Override
    public List<StaffAssignment> findAll() {
        return jpa.findAllByOrderByAssignedAt().stream().map(StaffAssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public List<StaffAssignment> findByUserId(UUID userId) {
        return jpa.findAllByUserIdOrderByAssignedAt(userId).stream().map(StaffAssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public boolean existsActive(UUID userId, UUID siteId) {
        return jpa.existsActive(userId, siteId);
    }

    @Override
    public Set<UUID> activeSiteIds(UUID userId, SiteType siteType) {
        return jpa.findAllByUserIdAndEndedAtIsNull(userId).stream()
                .filter(assignment -> assignment.siteType() == siteType)
                .map(StaffAssignmentJpaEntity::siteId)
                .collect(Collectors.toSet());
    }

    @Override
    public StaffAssignment save(StaffAssignment assignment) {
        if (assignment.id() == null) {
            return jpa.saveAndFlush(new StaffAssignmentJpaEntity(assignment)).toDomain();
        }
        StaffAssignmentJpaEntity entity = Versions.load(jpa, assignment.id(), assignment.version(),
                StaffAssignmentJpaEntity.class);
        entity.apply(assignment);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
