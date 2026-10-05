package pe.buildshield.core.organization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataWorksiteRepository extends JpaRepository<WorksiteJpaEntity, UUID> {
}
