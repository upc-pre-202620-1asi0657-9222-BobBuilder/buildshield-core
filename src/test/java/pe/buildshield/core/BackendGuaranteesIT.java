package pe.buildshield.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.buildshield.core.shared.idempotency.IdempotencyKeyPurger;
import pe.buildshield.core.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ContainersConfig.class, BackendGuaranteesIT.ControlledClock.class})
class BackendGuaranteesIT {
    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledClock {
        @Bean @Primary MutableClock clock() { return new MutableClock(Instant.now()); }
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired IdempotencyKeyPurger purger;
    HttpTestSession api;
    HttpTestSession.Fixture f;

    @BeforeEach void prepare() throws Exception {
        clock.setTo(Instant.now());
        api = new HttpTestSession(port, json);
        f = api.fixture();
    }

    @Test void repeated_entry_commits_one_balance_movement_and_audit_event() throws Exception {
        UUID key = UUID.randomUUID();
        var first = api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
        assertThat(first.status()).isEqualTo(201);
        for (int i = 0; i < 4; i++) {
            var replay = api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
            assertThat(replay.status()).isEqualTo(201);
            assertThat(replay.text()).isEqualTo(first.text());
            assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        }
        assertThat(balance()).isEqualByComparingTo("10");
        assertThat(movements()).isEqualTo(1);
        assertThat(events("STOCK_ADDED")).isEqualTo(1);
    }

    @Test void missing_key_and_changed_data_do_not_change_stock() throws Exception {
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), null).status()).isEqualTo(400);
        UUID key = UUID.randomUUID();
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key).status()).isEqualTo(201);
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(20), f.tenant().token(), key).status()).isEqualTo(400);
        assertThat(api.call("POST", "/api/v1/stock/entries", Map.of("warehouseId", f.warehouse(), "materialId", UUID.randomUUID(), "quantity", 10), f.tenant().token(), key).status()).isEqualTo(400);
        assertThat(api.call("POST", "/api/v1/stock/entries", Map.of("warehouseId", UUID.randomUUID(), "materialId", f.material(), "quantity", 10), f.tenant().token(), key).status()).isEqualTo(400);
        String reordered = "{\"quantity\":10,\"materialId\":\"" + f.material() + "\",\"warehouseId\":\"" + f.warehouse() + "\"}";
        assertThat(api.call("POST", "/api/v1/stock/entries", reordered, f.tenant().token(), key).status()).isEqualTo(201);
        assertThat(balance()).isEqualByComparingTo("10");
    }

    @Test void simultaneous_requests_with_one_key_have_one_effect() throws Exception {
        UUID key = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<HttpTestSession.Reply> request = () -> {
                start.await();
                return api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
            };
            var one = pool.submit(request);
            var two = pool.submit(request);
            start.countDown();
            var results = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(results).allSatisfy(result -> assertThat(result.status()).isIn(201, 409));
            assertThat(results.stream().filter(result -> result.status() == 201).count()).isGreaterThanOrEqualTo(1);
            assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key).status()).isEqualTo(201);
            assertThat(balance()).isEqualByComparingTo("10");
            assertThat(movements()).isEqualTo(1);
            assertThat(events("STOCK_ADDED")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void response_lost_after_commit_is_recovered_without_another_entry() throws Exception {
        UUID key = UUID.randomUUID();
        byte[] payload = json.writeValueAsBytes(f.entry(10));
        try (var socket = new java.net.Socket("127.0.0.1", port)) {
            String headers = "POST /api/v1/stock/entries HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                    + "Authorization: Bearer " + f.tenant().token() + "\r\nIdempotency-Key: " + key
                    + "\r\nContent-Length: " + payload.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().write(payload); socket.getOutputStream().flush();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE organization_id = ? AND idempotency_key = ?",
                    Long.class, f.tenant().organizationId(), key) == 0 && System.nanoTime() < until) Thread.sleep(20);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE organization_id = ? AND idempotency_key = ?",
                    Long.class, f.tenant().organizationId(), key)).isEqualTo(1);
            // El cliente cierra la conexión sin leer la respuesta confirmada.
        }
        var recovered = api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
        assertThat(recovered.status()).isEqualTo(201);
        assertThat(recovered.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(balance()).isEqualByComparingTo("10");
        assertThat(movements()).isEqualTo(1);
    }

    @Test void expired_response_keeps_its_operation_mark_after_purging() throws Exception {
        UUID key = UUID.randomUUID();
        api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
        clock.advance(Duration.ofHours(25));
        purger.purge();
        String freshToken = api.signIn(f.tenant().email());
        var repeated = api.call("POST", "/api/v1/stock/entries", f.entry(10), freshToken, key);
        assertThat(repeated.status()).isEqualTo(409);
        assertThat(repeated.text()).contains("IDEMPOTENCY_RESULT_EXPIRED");
        assertThat(balance()).isEqualByComparingTo("10");
        assertThat(movements()).isEqualTo(1);
    }

    @Test void legacy_operation_without_identity_is_never_executed_again() throws Exception {
        UUID key = UUID.randomUUID();
        jdbc.update("INSERT INTO idempotency_keys (organization_id,idempotency_key,request_method,request_path,response_status,created_at) VALUES (?,?,'POST','/api/v1/stock/entries',201,now())",
                f.tenant().organizationId(), key);
        var result = api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
        assertThat(result.status()).isEqualTo(409);
        assertThat(result.text()).contains("IDEMPOTENCY_LEGACY_RECORD");
        assertThat(movements()).isZero();
    }

    @Test void failed_audit_rolls_back_stock_movement_and_key_then_retry_succeeds() throws Exception {
        UUID key = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION audit.fail_test_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action = 'STOCK_ADDED' THEN RAISE EXCEPTION 'Fallo de auditoría simulado'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER fail_test_insert BEFORE INSERT ON audit.events FOR EACH ROW EXECUTE FUNCTION audit.fail_test_insert()");
        try {
            var failed = api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
            assertThat(failed.status()).isEqualTo(500);
            assertThat(movements()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.stock_items WHERE organization_id = ?", Long.class, f.tenant().organizationId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE organization_id = ? AND idempotency_key = ?", Long.class, f.tenant().organizationId(), key)).isZero();
            assertThat(events("STOCK_ADDED")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_test_insert ON audit.events");
            jdbc.execute("DROP FUNCTION audit.fail_test_insert()");
        }
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key).status()).isEqualTo(201);
        assertThat(balance()).isEqualByComparingTo("10");
    }

    @Test void failed_movement_rolls_back_the_balance_and_allows_retry() throws Exception {
        UUID key = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION inventory.fail_test_movement() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Fallo de movimiento simulado'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_test_movement BEFORE INSERT ON inventory.stock_movements FOR EACH ROW EXECUTE FUNCTION inventory.fail_test_movement()");
        try {
            assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key).status()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory.stock_items WHERE organization_id = ?", Long.class, f.tenant().organizationId())).isZero();
            assertThat(events("STOCK_ADDED")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_test_movement ON inventory.stock_movements");
            jdbc.execute("DROP FUNCTION inventory.fail_test_movement()");
        }
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key).status()).isEqualTo(201);
    }

    @Test void other_user_cannot_replay_and_unassigned_user_cannot_write() throws Exception {
        UUID key = UUID.randomUUID();
        api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
        String email = UUID.randomUUID() + "@buildshield.test";
        api.create("/api/v1/users", Map.of("fullName", "Sin asignación", "email", email,
                "role", "WAREHOUSE_MANAGER", "password", HttpTestSession.PASSWORD), f.tenant().token());
        String token = api.signIn(email);
        var replay = api.call("POST", "/api/v1/stock/entries", f.entry(10), token, key);
        assertThat(replay.status()).isEqualTo(403);
        assertThat(replay.text()).contains("IDEMPOTENCY_OWNER_MISMATCH");
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), token).status()).isEqualTo(404);
        assertThat(balance()).isEqualByComparingTo("10");
        assertThat(events("ACCESS_DENIED")).isEqualTo(2);
    }

    @Test void removing_assignment_also_denies_a_replay_by_the_original_actor() throws Exception {
        String email = UUID.randomUUID() + "@buildshield.test";
        UUID user = api.create("/api/v1/users", Map.of("fullName", "Encargado", "email", email, "role", "WAREHOUSE_MANAGER",
                "password", HttpTestSession.PASSWORD), f.tenant().token());
        UUID assignment = api.create("/api/v1/assignments", Map.of("userId", user, "siteType", "WAREHOUSE", "siteId", f.warehouse()), f.tenant().token());
        String token = api.signIn(email);
        UUID key = UUID.randomUUID();
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), token, key).status()).isEqualTo(201);
        assertThat(api.call("PATCH", "/api/v1/assignments/" + assignment, Map.of("active", false), f.tenant().token()).status()).isEqualTo(200);
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), token, key).status()).isEqualTo(403);
        assertThat(balance()).isEqualByComparingTo("10");
    }

    @Test void keys_are_independent_by_company_and_audit_is_scoped() throws Exception {
        var other = api.fixture();
        UUID key = UUID.randomUUID();
        api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token(), key);
        assertThat(api.call("POST", "/api/v1/stock/entries", other.entry(20), other.tenant().token(), key).status()).isEqualTo(201);
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(10), other.tenant().token()).status()).isEqualTo(404);
        var history = api.call("GET", "/api/v1/audit/events", null, other.tenant().token());
        assertThat(history.status()).isEqualTo(200);
        history.data().forEach(event -> assertThat(event.path("organizationId").asText()).isEqualTo(other.tenant().organizationId().toString()));
        assertThat(api.call("GET", "/api/v1/audit/events", null, f.siteToken()).status()).isEqualTo(403);
        assertThat(api.call("GET", "/api/v1/audit/events?size=101", null, f.tenant().token()).status()).isEqualTo(400);
        assertThat(api.call("GET", "/api/v1/audit/events?page=-1", null, f.tenant().token()).status()).isEqualTo(400);
        assertThat(balance()).isEqualByComparingTo("10");
    }

    @Test void create_approve_and_reject_orders_are_recoverable_and_audited_without_secrets() throws Exception {
        UUID createKey = UUID.randomUUID();
        var created = api.call("POST", "/api/v1/orders", f.order(), f.siteToken(), createKey);
        assertThat(created.status()).isEqualTo(201);
        UUID id = UUID.fromString(created.data().path("id").asText());
        var replay = api.call("POST", "/api/v1/orders", f.order(), f.siteToken(), createKey);
        assertThat(replay.text()).isEqualTo(created.text());
        assertThat(replay.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));
        UUID approvalKey = UUID.randomUUID();
        var approved = api.call("POST", "/api/v1/orders/" + id + "/approve", null, f.tenant().token(), approvalKey);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.data().path("status").asText()).isEqualTo("IN_REVIEW");
        assertThat(api.call("POST", "/api/v1/orders/" + id + "/approve", null, f.tenant().token(), approvalKey).text()).isEqualTo(approved.text());
        assertThat(api.call("POST", "/api/v1/orders/" + id + "/reject", Map.of("reason", "No"), f.tenant().token()).status()).isEqualTo(409);
        UUID second = api.create("/api/v1/orders", f.order(), f.siteToken());
        UUID rejectionKey = UUID.randomUUID();
        var rejected = api.call("POST", "/api/v1/orders/" + second + "/reject", Map.of("reason", "Sin transporte"), f.tenant().token(), rejectionKey);
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.data().path("status").asText()).isEqualTo("CANCELLED");
        assertThat(api.call("POST", "/api/v1/orders/" + second + "/reject", Map.of("reason", "Sin transporte"), f.tenant().token(), rejectionKey).text()).isEqualTo(rejected.text());
        var history = api.call("GET", "/api/v1/audit/events?resourceType=ORDER&resourceId=" + id, null, f.tenant().token());
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.data()).hasSize(2);
        assertThat(history.text()).doesNotContain(HttpTestSession.PASSWORD, f.tenant().token(), "passwordHash", "refreshToken");
        assertThat(events("ORDER_CREATED")).isEqualTo(2);
        assertThat(events("ORDER_APPROVED")).isEqualTo(1);
        assertThat(events("ORDER_REJECTED")).isEqualTo(1);
        assertThat(api.call("GET", "/api/v1/orders/" + id, null, f.siteToken()).status()).isEqualTo(200);
    }

    @Test void simultaneous_order_decisions_leave_one_valid_state_and_one_audit_event() throws Exception {
        UUID id = api.create("/api/v1/orders", f.order(), f.siteToken());
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var approve = pool.submit(() -> { start.await(); return api.call("POST", "/api/v1/orders/" + id + "/approve", null, f.tenant().token()); });
            var reject = pool.submit(() -> { start.await(); return api.call("POST", "/api/v1/orders/" + id + "/reject", Map.of("reason", "Sin transporte"), f.tenant().token()); });
            start.countDown();
            var results = List.of(approve.get(15, TimeUnit.SECONDS), reject.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(result -> result.status() == 200).count()).isEqualTo(1);
            assertThat(results.stream().filter(result -> result.status() == 409).count()).isEqualTo(1);
            assertThat(events("ORDER_APPROVED") + events("ORDER_REJECTED")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void audit_and_movements_cannot_be_changed_deleted_or_truncated() throws Exception {
        api.call("POST", "/api/v1/stock/entries", f.entry(10), f.tenant().token());
        for (String table : List.of("audit.events", "inventory.stock_movements")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET organization_id = organization_id WHERE organization_id = ?", f.tenant().organizationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table + " WHERE organization_id = ?", f.tenant().organizationId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE " + table)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThat(movements()).isEqualTo(1);
        assertThat(events("STOCK_ADDED")).isEqualTo(1);
    }

    private BigDecimal balance() { return jdbc.queryForObject("SELECT available_qty FROM inventory.stock_items WHERE organization_id = ? AND location_id = ? AND material_id = ?", BigDecimal.class, f.tenant().organizationId(), f.warehouse(), f.material()); }
    private long movements() { return jdbc.queryForObject("SELECT count(*) FROM inventory.stock_movements WHERE organization_id = ?", Long.class, f.tenant().organizationId()); }
    private long events(String action) { return jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE organization_id = ? AND action = ?", Long.class, f.tenant().organizationId(), action); }
}
