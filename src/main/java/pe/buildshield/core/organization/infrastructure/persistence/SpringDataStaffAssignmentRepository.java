package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataStaffAssignmentRepository extends JpaRepository<StaffAssignmentJpaEntity, UUID> {

    List<StaffAssignmentJpaEntity> findAllByOrderByAssignedAt();

    List<StaffAssignmentJpaEntity> findAllByUserIdOrderByAssignedAt(UUID userId);

    List<StaffAssignmentJpaEntity> findAllByUserIdAndEndedAtIsNull(UUID userId);

    @Query("""
            SELECT COUNT(a) > 0 FROM StaffAssignmentJpaEntity a
            WHERE a.userId = :userId AND a.endedAt IS NULL AND (a.worksiteId = :siteId OR a.warehouseId = :siteId)
            """)
    boolean existsActive(UUID userId, UUID siteId);
}
