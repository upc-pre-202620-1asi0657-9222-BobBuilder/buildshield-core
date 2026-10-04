package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataMaterialRepository extends JpaRepository<MaterialJpaEntity, UUID> {

    Optional<MaterialJpaEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    List<MaterialJpaEntity> findAllByOrderBySku();

    List<MaterialJpaEntity> findAllByActiveTrueOrderBySku();
}
