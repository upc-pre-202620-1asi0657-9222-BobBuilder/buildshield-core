package pe.buildshield.core.shared.security;

/**
 * Lista de revocación de tokens (por ejemplo, al cerrar sesión). Cada unidad la implementa con
 * su propio almacenamiento.
 */
public interface RevokedTokenStore {

    /** @param tokenId valor del claim {@code jti} */
    boolean isRevoked(String tokenId);
}
