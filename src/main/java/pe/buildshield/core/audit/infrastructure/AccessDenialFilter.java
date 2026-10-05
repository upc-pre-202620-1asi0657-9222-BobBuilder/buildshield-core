package pe.buildshield.core.audit.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.buildshield.core.audit.AuditTrail;
import pe.buildshield.core.shared.tenant.TenantContext;
import java.io.IOException;
import java.util.UUID;

/** Envuelve la idempotencia: registra denegaciones tras cerrar su transacción. */
public class AccessDenialFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AccessDenialFilter.class);
    private final AuditTrail audit;
    public AccessDenialFilter(AuditTrail audit) { this.audit = audit; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var actor = TenantContext.current();
        chain.doFilter(request, response);
        if (actor.isPresent() && (response.getStatus() == 403 || response.getStatus() == 404)) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.length() > 500) path = path.substring(0, 500);
            UUID operation = null;
            try { operation = UUID.fromString(request.getHeader("Idempotency-Key")); }
            catch (IllegalArgumentException | NullPointerException ignored) { }
            try { audit.denied(actor.get(), request.getMethod(), path, response.getStatus(), operation); }
            catch (RuntimeException ex) {
                LOG.error("No se pudo registrar denegación: organization={}, actor={}, status={}",
                        actor.get().organizationId(), actor.get().userId(), response.getStatus());
            }
        }
    }
}
