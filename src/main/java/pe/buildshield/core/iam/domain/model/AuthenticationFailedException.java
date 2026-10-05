package pe.buildshield.core.iam.domain.model;

/**
 * Credenciales o token de renovación inválidos. Se responde 401 con un mensaje que no revela si el
 * correo existe.
 */
public class AuthenticationFailedException extends RuntimeException {

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    private final String code;

    private AuthenticationFailedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(INVALID_CREDENTIALS, "Correo o contraseña incorrectos");
    }

    public static AuthenticationFailedException invalidRefreshToken() {
        return new AuthenticationFailedException(INVALID_REFRESH_TOKEN,
                "La sesión venció o fue cerrada; inicia sesión nuevamente");
    }

    public String getCode() {
        return code;
    }
}
