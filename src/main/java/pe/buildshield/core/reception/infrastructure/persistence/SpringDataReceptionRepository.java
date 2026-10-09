package pe.buildshield.core.reception.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface SpringDataReceptionRepository extends JpaRepository<ReceptionJpaEntity, UUID> {

    /** La recepción y sus líneas en una sola consulta (cotejo sin N+1). */
    @Query("SELECT DISTINCT r FROM ReceptionJpaEntity r LEFT JOIN FETCH r.lines WHERE r.id = :id")
    Optional<ReceptionJpaEntity> findWithLines(UUID id);

    @Query("SELECT r.id FROM ReceptionJpaEntity r WHERE r.dispatchId = :dispatchId")
    Optional<UUID> findIdByDispatchId(UUID dispatchId);
}
