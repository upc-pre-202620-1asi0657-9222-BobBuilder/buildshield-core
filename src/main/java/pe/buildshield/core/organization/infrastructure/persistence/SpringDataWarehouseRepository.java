package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataWarehouseRepository extends JpaRepository<WarehouseJpaEntity, UUID> {

    List<WarehouseJpaEntity> findAllByActiveTrueOrderByName();
}
