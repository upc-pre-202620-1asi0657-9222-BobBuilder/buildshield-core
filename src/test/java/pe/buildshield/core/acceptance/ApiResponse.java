package pe.buildshield.core.acceptance;

/** Respuesta HTTP real del Core: código y cuerpo como texto. */
public record ApiResponse(int status, String body) {
}
