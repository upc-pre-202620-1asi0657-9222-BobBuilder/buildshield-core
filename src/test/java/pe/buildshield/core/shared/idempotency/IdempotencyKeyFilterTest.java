package pe.buildshield.core.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.core.shared.error.ErrorResponseWriter;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.shared.testsupport.InMemoryTransactionManager;
import pe.buildshield.core.shared.testsupport.MutableClock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyFilterTest {

    private static final TenantInfo TENANT = new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "RESIDENT");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T12:00:00Z"));
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();
    private final IdempotencyKeyFilter filter = new IdempotencyKeyFilter(store, new TransactionTemplate(transactionManager),
            new ErrorResponseWriter(new ObjectMapper()), clock, List.of("/api/v1/receptions/**"));
    private final AtomicInteger effects = new AtomicInteger();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void new_key_processes_once_and_stores_the_result_in_the_same_transaction() throws Exception {
        UUID key = UUID.randomUUID();

        MockHttpServletResponse response = send(key, createdChain());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":1}");
        assertThat(effects).hasValue(1);
        assertThat(store.size()).isEqualTo(1);
        assertThat(transactionManager.commits).hasValue(1);
    }

    @Test
    void processed_key_returns_the_stored_response_without_executing_again() throws Exception {
        UUID key = UUID.randomUUID();
        send(key, createdChain());

        MockHttpServletResponse replay = send(key, createdChain());

        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).isEqualTo("{\"id\":1}");
        assertThat(replay.getContentType()).isEqualTo("application/json");
        assertThat(replay.getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(effects).hasValue(1);
    }

    @Test
    void two_simultaneous_requests_with_the_same_key_execute_once_and_the_second_gets_409() throws Exception {
        UUID key = UUID.randomUUID();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        FilterChain slowChain = (req, res) -> {
            firstInside.countDown();
            await(releaseFirst);
            createdChain().doFilter(req, res);
        };

        CompletableFuture<MockHttpServletResponse> first = CompletableFuture.supplyAsync(() -> sendUnchecked(key, slowChain));
        assertThat(firstInside.await(5, TimeUnit.SECONDS)).isTrue();

        MockHttpServletResponse second = send(key, createdChain());
        releaseFirst.countDown();
        MockHttpServletResponse firstResponse = first.get(5, TimeUnit.SECONDS);
        MockHttpServletResponse third = send(key, createdChain());

        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(second.getContentAsString()).contains("IDEMPOTENCY_IN_PROGRESS");
        assertThat(firstResponse.getStatus()).isEqualTo(201);
        assertThat(third.getStatus()).isEqualTo(201);
        assertThat(third.getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(effects).hasValue(1);
    }

    @Test
    void failed_operation_rolls_back_and_frees_the_key_for_a_retry() throws Exception {
        UUID key = UUID.randomUUID();
        FilterChain failing = (req, res) -> ((HttpServletResponse) res).setStatus(503);

        MockHttpServletResponse failed = send(key, failing);
        MockHttpServletResponse retry = send(key, createdChain());

        assertThat(failed.getStatus()).isEqualTo(503);
        assertThat(transactionManager.rollbacks).hasValue(1);
        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(retry.getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isNull();
        assertThat(effects).hasValue(1);
    }

    @Test
    void business_error_is_not_stored() throws Exception {
        UUID key = UUID.randomUUID();
        FilterChain conflict = (req, res) -> ((HttpServletResponse) res).setStatus(409);

        send(key, conflict);

        assertThat(store.size()).isZero();
        assertThat(transactionManager.rollbacks).hasValue(1);
    }

    @Test
    void exception_in_the_chain_rolls_back_and_propagates() {
        UUID key = UUID.randomUUID();
        FilterChain throwing = (req, res) -> {
            throw new ServletException("falló el despacho");
        };

        assertThatThrownBy(() -> send(key, throwing)).isInstanceOf(ServletException.class);
        assertThat(transactionManager.rollbacks).hasValue(1);
        assertThat(store.size()).isZero();

        FilterChain ioFailure = (req, res) -> {
            throw new IOException("conexión cerrada");
        };
        assertThatThrownBy(() -> send(key, ioFailure)).isInstanceOf(IOException.class);
    }

    @Test
    void same_key_for_another_operation_is_rejected() throws Exception {
        UUID key = UUID.randomUUID();
        send(key, createdChain());

        MockHttpServletRequest other = request("POST", "/api/v1/orders", key);
        MockHttpServletResponse response = run(other, createdChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(effects).hasValue(1);
    }

    @Test
    void stored_result_expires_after_24_hours() throws Exception {
        UUID key = UUID.randomUUID();
        send(key, createdChain());

        clock.advance(Duration.ofHours(23));
        assertThat(send(key, createdChain()).getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isEqualTo("true");

        clock.advance(Duration.ofHours(2));
        assertThat(send(key, createdChain()).getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isNull();
        assertThat(effects).hasValue(2);
    }

    @Test
    void purger_deletes_keys_older_than_24_hours() throws Exception {
        send(UUID.randomUUID(), createdChain());
        IdempotencyKeyPurger purger = new IdempotencyKeyPurger(store, new TransactionTemplate(transactionManager), clock);

        assertThat(purger.purge()).isZero();
        clock.advance(Duration.ofHours(25));
        assertThat(purger.purge()).isEqualTo(1);
        assertThat(store.size()).isZero();
    }

    @Test
    void key_is_required_on_configured_paths() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/receptions", null);

        MockHttpServletResponse response = run(request, createdChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(effects).hasValue(0);
    }

    @Test
    void key_is_optional_elsewhere() throws Exception {
        MockHttpServletResponse response = run(request("POST", "/api/v1/orders", null), createdChain());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(store.size()).isZero();
    }

    @Test
    void key_must_be_a_uuid() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/receptions", null);
        request.addHeader(IdempotencyKeyFilter.HEADER, "123");

        MockHttpServletResponse response = run(request, createdChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void safe_methods_are_not_filtered() throws Exception {
        MockHttpServletRequest get = request("GET", "/api/v1/receptions", UUID.randomUUID());

        run(get, createdChain());
        run(get, createdChain());

        assertThat(effects).hasValue(2);
        assertThat(transactionManager.commits).hasValue(0);
    }

    @Test
    void without_organization_the_request_passes_through() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/receptions", UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, createdChain());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(store.size()).isZero();
    }

    private FilterChain createdChain() {
        return (req, res) -> {
            int id = effects.incrementAndGet();
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.setContentType("application/json");
            http.getOutputStream().write(("{\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8));
        };
    }

    private MockHttpServletResponse send(UUID key, FilterChain chain) throws Exception {
        return run(request("POST", "/api/v1/receptions", key), chain);
    }

    private MockHttpServletResponse sendUnchecked(UUID key, FilterChain chain) {
        try {
            return send(key, chain);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        TenantContext.set(TENANT);
        try {
            filter.doFilter(request, response, chain);
        } finally {
            TenantContext.clear();
        }
        return response;
    }

    private static MockHttpServletRequest request(String method, String uri, UUID key) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (key != null) {
            request.addHeader(IdempotencyKeyFilter.HEADER, key.toString());
        }
        return request;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
