package pe.buildshield.core.iam.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    List<UserJpaEntity> findAllByOrderByEmail();

    /** SQL nativo: Hibernate no le aplica el filtro de organización, así revisa todas. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM iam.users WHERE email = :email)", nativeQuery = true)
    boolean existsByEmailInAnyOrganization(String email);
}
