package pe.buildshield.core.organization;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que organization necesita saber de un usuario para asignarlo (US17). Lo implementa el módulo
 * iam: así la dependencia va de iam hacia organization y no se forma un ciclo entre módulos.
 */
public interface StaffDirectory {

    /** Usuario de la organización del contexto; vacío si no existe o es de otra organización. */
    Optional<StaffMember> findMember(UUID userId);

    /**
     * @param role nombre del rol en iam (ADMINISTRATOR, WAREHOUSE_MANAGER, SITE_MANAGER)
     */
    record StaffMember(UUID userId, String role, boolean active) {
    }
}
