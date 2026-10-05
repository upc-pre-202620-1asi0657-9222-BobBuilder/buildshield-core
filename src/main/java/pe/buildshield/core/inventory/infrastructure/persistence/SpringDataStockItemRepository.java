package pe.buildshield.core.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataStockItemRepository extends JpaRepository<StockItemJpaEntity, UUID> {

    List<StockItemJpaEntity> findAllByOrderByLocationIdAscMaterialIdAsc();
}
