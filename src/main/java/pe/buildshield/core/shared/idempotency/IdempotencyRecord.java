package pe.buildshield.core.shared.idempotency;

import java.time.Instant;
import java.util.UUID;

/** Resultado guardado de una petición procesada con una {@code Idempotency-Key}. */
public record IdempotencyRecord(
        UUID organizationId,
        UUID key,
        String requestMethod,
        String requestPath,
        int responseStatus,
        String responseContentType,
        byte[] responseBody,
        Instant createdAt) {

    boolean isSameRequest(String method, String path) {
        return requestMethod.equals(method) && requestPath.equals(path);
    }
}
