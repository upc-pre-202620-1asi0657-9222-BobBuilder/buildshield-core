package pe.buildshield.core.iam.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.core.iam.domain.model.RefreshToken;
import pe.buildshield.core.iam.domain.model.TokenRepositories.RefreshTokenRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;

    JpaRefreshTokenRepository(SpringDataRefreshTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public void save(RefreshToken token) {
        jpa.save(RefreshTokenJpaEntity.from(token));
    }

    @Override
    public void revokeAllActive(UUID userId, Instant now) {
        jpa.revokeAllActive(userId, now);
    }
}
