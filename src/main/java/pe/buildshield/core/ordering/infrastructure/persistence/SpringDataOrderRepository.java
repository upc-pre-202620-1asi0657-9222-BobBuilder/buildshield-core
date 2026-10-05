package pe.buildshield.core.ordering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    List<OrderJpaEntity> findAllByOrderByPlacedAtDesc();

    @Query("SELECT o FROM OrderJpaEntity o WHERE o.worksiteId IN :sites OR o.warehouseId IN :sites ORDER BY o.placedAt DESC")
    List<OrderJpaEntity> findBySites(Collection<UUID> sites);
}
