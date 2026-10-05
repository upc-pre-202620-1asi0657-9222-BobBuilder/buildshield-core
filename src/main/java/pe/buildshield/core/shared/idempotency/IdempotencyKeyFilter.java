package pe.buildshield.core.shared.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import pe.buildshield.core.shared.error.ErrorResponse;
import pe.buildshield.core.shared.error.ErrorResponseWriter;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hace repetibles las operaciones que traen la cabecera {@value #HEADER} (un UUID generado por el
 * navegador):
 *
 * <ul>
 *   <li>Clave ya procesada (últimas 24 h): devuelve la respuesta guardada sin volver a ejecutar.</li>
 *   <li>Clave en curso en otra petición: 409.</li>
 *   <li>Clave nueva: procesa y guarda clave y resultado en la <b>misma transacción</b> que el efecto
 *       de negocio. Si la operación falla, se deshace todo y la clave queda libre.</li>
 * </ul>
 *
 * <p>Solo se guardan respuestas 2xx. Debe ejecutarse después de la autenticación, porque la clave se
 * guarda por organización.
 */
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    public static final Duration RETENTION = Duration.ofHours(24);

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyStore store;
    private final TransactionTemplate transactionTemplate;
    private final ErrorResponseWriter errorWriter;
    private final Clock clock;
    private final List<String> requiredPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public IdempotencyKeyFilter(IdempotencyStore store, TransactionTemplate transactionTemplate,
            ErrorResponseWriter errorWriter, Clock clock, List<String> requiredPaths) {
        this.store = store;
        this.transactionTemplate = transactionTemplate;
        this.errorWriter = errorWriter;
        this.clock = clock;
        this.requiredPaths = List.copyOf(requiredPaths);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !UNSAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            if (isRequired(path)) {
                badRequest(response, "IDEMPOTENCY_KEY_REQUIRED", "Esta operación requiere la cabecera " + HEADER);
            } else {
                chain.doFilter(request, response);
            }
            return;
        }
        UUID key;
        try {
            key = UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            badRequest(response, "INVALID_IDEMPOTENCY_KEY", "La cabecera " + HEADER + " debe ser un UUID");
            return;
        }
        Optional<TenantInfo> tenant = TenantContext.current();
        if (tenant.isEmpty()) {
            // Sin organización no hay a quién asociar la clave; la seguridad decide sobre la petición.
            chain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper bufferedResponse = new ContentCachingResponseWrapper(response);
        Outcome outcome = process(tenant.get().organizationId(), key, request, path, bufferedResponse, chain);

        switch (outcome.kind()) {
            case IN_PROGRESS -> errorWriter.write(response, HttpStatus.CONFLICT.value(), ErrorResponse.of(
                    "IDEMPOTENCY_IN_PROGRESS", "Una petición con la misma " + HEADER + " está en curso"));
            case KEY_REUSED -> badRequest(response, "IDEMPOTENCY_KEY_REUSED",
                    "La " + HEADER + " ya se usó para otra operación");
            case REPLAY -> replay(response, outcome.record());
            case EXECUTED -> bufferedResponse.copyBodyToResponse();
        }
    }

    private Outcome process(UUID organizationId, UUID key, HttpServletRequest request, String path,
            ContentCachingResponseWrapper bufferedResponse, FilterChain chain) throws ServletException, IOException {
        try {
            return transactionTemplate.execute(status -> {
                if (!store.tryLock(organizationId, key)) {
                    return Outcome.of(Kind.IN_PROGRESS);
                }
                Instant now = clock.instant();
                Optional<IdempotencyRecord> existing = store.find(organizationId, key, now.minus(RETENTION));
                if (existing.isPresent()) {
                    return existing.get().isSameRequest(request.getMethod(), path)
                            ? new Outcome(Kind.REPLAY, existing.get())
                            : Outcome.of(Kind.KEY_REUSED);
                }

                invoke(chain, request, bufferedResponse);

                int responseStatus = bufferedResponse.getStatus();
                if (status.isRollbackOnly() || responseStatus < 200 || responseStatus >= 300) {
                    status.setRollbackOnly();
                } else {
                    store.save(new IdempotencyRecord(organizationId, key, request.getMethod(), path, responseStatus,
                            bufferedResponse.getContentType(), bufferedResponse.getContentAsByteArray(), now));
                }
                return Outcome.of(Kind.EXECUTED);
            });
        } catch (ChainException ex) {
            if (ex.getCause() instanceof ServletException servletException) {
                throw servletException;
            }
            throw (IOException) ex.getCause();
        }
    }

    private static void invoke(FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException ex) {
            throw new ChainException(ex);
        }
    }

    private static void replay(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        response.setStatus(record.responseStatus());
        response.setHeader(REPLAYED_HEADER, "true");
        if (record.responseContentType() != null) {
            response.setContentType(record.responseContentType());
        }
        if (record.responseBody() != null) {
            response.getOutputStream().write(record.responseBody());
        }
    }

    private boolean isRequired(String path) {
        return requiredPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void badRequest(HttpServletResponse response, String code, String message) throws IOException {
        errorWriter.write(response, HttpStatus.BAD_REQUEST.value(), ErrorResponse.of(code, message));
    }

    private enum Kind { IN_PROGRESS, KEY_REUSED, REPLAY, EXECUTED }

    private record Outcome(Kind kind, IdempotencyRecord record) {
        static Outcome of(Kind kind) {
            return new Outcome(kind, null);
        }
    }

    /** Transporta las excepciones verificadas de la cadena fuera de la transacción (que se deshace). */
    private static final class ChainException extends RuntimeException {
        ChainException(Exception cause) {
            super(cause);
        }
    }
}
