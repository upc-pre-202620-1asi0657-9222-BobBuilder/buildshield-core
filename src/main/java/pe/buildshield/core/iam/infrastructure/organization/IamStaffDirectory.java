package pe.buildshield.core.iam.infrastructure.organization;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.iam.domain.model.UserRepository;
import pe.buildshield.core.organization.StaffDirectory;

import java.util.Optional;
import java.util.UUID;

/** iam provee a organization los datos de un usuario para asignarlo a obras y almacenes. */
@Component
class IamStaffDirectory implements StaffDirectory {

    private final UserRepository users;

    IamStaffDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffMember> findMember(UUID userId) {
        return users.findById(userId).map(user -> new StaffMember(user.id(), user.role().name(), user.active()));
    }
}
