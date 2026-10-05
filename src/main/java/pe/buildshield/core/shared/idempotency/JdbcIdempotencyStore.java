package pe.buildshield.core.shared.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación para PostgreSQL sobre la tabla {@code idempotency_keys}. "En curso" se detecta con
 * un advisory lock de transacción ({@code pg_try_advisory_xact_lock}), que no bloquea y se libera
 * solo al confirmar o deshacer.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final RowMapper<IdempotencyRecord> ROW_MAPPER = (rs, rowNum) -> new IdempotencyRecord(
            rs.getObject("organization_id", UUID.class),
            rs.getObject("idempotency_key", UUID.class),
            rs.getString("request_method"),
            rs.getString("request_path"),
            rs.getInt("response_status"),
            rs.getString("response_content_type"),
            rs.getBytes("response_body"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryLock(UUID organizationId, UUID key) {
        Boolean acquired = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class,
                lockId(organizationId, key));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public Optional<IdempotencyRecord> find(UUID organizationId, UUID key, Instant notBefore) {
        return jdbc.query("""
                        SELECT * FROM idempotency_keys
                        WHERE organization_id = ? AND idempotency_key = ? AND created_at >= ?
                        """, ROW_MAPPER, organizationId, key, Timestamp.from(notBefore))
                .stream().findFirst();
    }

    @Override
    public void save(IdempotencyRecord record) {
        jdbc.update("""
                        INSERT INTO idempotency_keys (organization_id, idempotency_key, request_method, request_path,
                                                      response_status, response_content_type, response_body, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (organization_id, idempotency_key) DO UPDATE SET
                            request_method = EXCLUDED.request_method,
                            request_path = EXCLUDED.request_path,
                            response_status = EXCLUDED.response_status,
                            response_content_type = EXCLUDED.response_content_type,
                            response_body = EXCLUDED.response_body,
                            created_at = EXCLUDED.created_at
                        """,
                record.organizationId(), record.key(), record.requestMethod(), record.requestPath(),
                record.responseStatus(), record.responseContentType(), record.responseBody(),
                Timestamp.from(record.createdAt()));
    }

    @Override
    public int deleteCreatedBefore(Instant threshold) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?", Timestamp.from(threshold));
    }

    /** Identificador de 64 bits del advisory lock para la pareja (organización, clave). */
    static long lockId(UUID organizationId, UUID key) {
        long hash = 1125899906842597L;
        for (long part : new long[] {organizationId.getMostSignificantBits(), organizationId.getLeastSignificantBits(),
                key.getMostSignificantBits(), key.getLeastSignificantBits()}) {
            hash = 31 * hash + part;
        }
        return hash;
    }
}
