package pe.buildshield.core.iam.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    /** Filtrado por la organización del contexto; en modo sistema busca en todas (inicio de sesión). */
    Optional<User> findByEmail(EmailAddress email);

    /** El correo identifica al usuario en el inicio de sesión: es único entre todas las organizaciones. */
    boolean existsByEmailInAnyOrganization(EmailAddress email);

    Optional<User> findById(UUID id);

    /** Usuarios de la organización del contexto. */
    List<User> findAll();

    /** Guarda y devuelve el usuario con su identificador y versión. */
    User save(User user);
}
