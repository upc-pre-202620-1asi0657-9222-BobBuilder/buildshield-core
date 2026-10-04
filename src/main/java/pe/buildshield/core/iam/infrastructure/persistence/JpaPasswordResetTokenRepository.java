package pe.buildshield.core.iam.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.buildshield.core.iam.domain.model.PasswordResetToken;
import pe.buildshield.core.iam.domain.model.TokenRepositories.PasswordResetTokenRepository;

import java.util.Optional;

@Repository
class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final SpringDataPasswordResetTokenRepository jpa;

    JpaPasswordResetTokenRepository(SpringDataPasswordResetTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(PasswordResetTokenJpaEntity::toDomain);
    }

    @Override
    public void save(PasswordResetToken token) {
        jpa.save(PasswordResetTokenJpaEntity.from(token));
    }
}
