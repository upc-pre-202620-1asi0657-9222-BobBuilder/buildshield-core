package pe.buildshield.core.dispatch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataDispatchRepository extends JpaRepository<DispatchJpaEntity, UUID> {

    List<DispatchJpaEntity> findAllByOrderByPreparedAtDesc();

    @Query("SELECT d FROM DispatchJpaEntity d WHERE d.warehouseId IN :sites OR d.worksiteId IN :sites ORDER BY d.preparedAt DESC")
    List<DispatchJpaEntity> findBySites(Collection<UUID> sites);
}
