package pe.buildshield.core.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.shared.correlation.CorrelationId;
import pe.buildshield.core.shared.idempotency.OperationContext;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Fachada mínima: historial de negocio en la misma transacción que sus efectos. */
@Service
public class AuditTrail {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    public AuditTrail(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.clock = clock;
    }

    /** Solo se reciben vistas públicas sin credenciales, nunca comandos ni entidades IAM. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T recorded(String action, String type, T publicView) {
        JsonNode details = json.valueToTree(publicView);
        record(action, type, UUID.fromString(details.path("id").asText()), details);
        return publicView;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String type, UUID resourceId, Object safeDetails) {
        append(TenantContext.require(), action, type, resourceId, safeDetails, OperationContext.current().orElse(null));
    }

    /** El intento rechazado sobrevive al rollback del negocio, y pertenece al solicitante. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(TenantInfo actor, String method, String path, int status, UUID operationId) {
        String[] segments = path.split("/");
        String type = segments.length > 3 ? switch (segments[3]) {
            case "orders" -> "ORDER"; case "worksites" -> "WORKSITE"; case "warehouses" -> "WAREHOUSE";
            case "materials" -> "MATERIAL"; case "assignments" -> "ASSIGNMENT"; case "users" -> "USER";
            case "stock" -> "STOCK_ITEM"; case "audit" -> "AUDIT_EVENT";
            case "dispatches" -> "DISPATCH"; case "receptions" -> "RECEPTION"; default -> "RESOURCE";
        } : "RESOURCE";
        UUID resource = null;
        if (segments.length > 4) {
            try { resource = UUID.fromString(segments[4]); }
            catch (IllegalArgumentException ignored) { }
        }
        append(actor, "ACCESS_DENIED", type, resource,
                java.util.Map.of("method", method, "path", path, "status", status), operationId);
    }

    private void append(TenantInfo actor, String action, String type, UUID resourceId, Object details, UUID operationId) {
        jdbc.update("""
                INSERT INTO audit.events (id, organization_id, actor_id, actor_role, action, resource_type,
                    resource_id, occurred_at, correlation_id, operation_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), actor.organizationId(), actor.userId(), actor.role(), action, type,
                resourceId, Timestamp.from(clock.instant()), MDC.get(CorrelationId.MDC_KEY), operationId,
                json.valueToTree(details).toString());
    }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public List<Event> list(String type, UUID resourceId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new pe.buildshield.core.shared.error.ValidationException(
                "INVALID_PAGINATION", "page debe ser >= 0 y size entre 1 y 100", List.of());
        UUID organization = TenantContext.require().organizationId();
        return jdbc.query("""
                SELECT * FROM audit.events WHERE organization_id = ?
                    AND (CAST(? AS varchar) IS NULL OR resource_type = ?)
                    AND (CAST(? AS uuid) IS NULL OR resource_id = ?)
                ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new Event(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("actor_id", UUID.class), rs.getString("actor_role"), rs.getString("action"),
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("correlation_id"),
                rs.getObject("operation_id", UUID.class), readDetails(rs.getString("details"))),
                organization, type, type, resourceId, resourceId, size, (long) page * size);
    }
    private JsonNode readDetails(String value) {
        try { return json.readTree(value); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Registro de auditoría inválido", ex); }
    }
    public record Event(UUID id, UUID organizationId, UUID actorId, String actorRole, String action,
                        String resourceType, UUID resourceId, Instant occurredAt, String correlationId,
                        UUID operationId, JsonNode details) { }
}
