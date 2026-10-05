package pe.buildshield.core.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.core.shared.error.ErrorResponseWriter;
import pe.buildshield.testapp.ClockTestConfig;
import pe.buildshield.testapp.Note;
import pe.buildshield.testapp.NoteRepository;
import pe.buildshield.testapp.PostgresIntegrationTest;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.shared.testsupport.MutableClock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Idempotencia completa contra PostgreSQL: advisory lock, tabla idempotency_keys y efecto en la misma transacción. */
@PostgresIntegrationTest
@Import(ClockTestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class IdempotencyKeyFilterIT {

    private static final TenantInfo TENANT = new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "RESIDENT");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    NoteRepository notes;

    @Autowired
    MutableClock clock;

    private JdbcIdempotencyStore store;
    private IdempotencyKeyFilter filter;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM test_notes");
        store = new JdbcIdempotencyStore(jdbc);
        filter = new IdempotencyKeyFilter(store, new TransactionTemplate(transactionManager),
                new ErrorResponseWriter(new ObjectMapper()), clock, List.of());
    }

    @Test
    void two_simultaneous_requests_with_the_same_key_create_one_reception() throws Exception {
        UUID key = UUID.randomUUID();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        FilterChain slowCreate = (req, res) -> {
            notes.save(new Note("recepción"));
            firstInside.countDown();
            await(releaseFirst);
            created(res);
        };

        CompletableFuture<MockHttpServletResponse> first = CompletableFuture.supplyAsync(() -> send(key, slowCreate));
        assertThat(firstInside.await(10, TimeUnit.SECONDS)).isTrue();

        MockHttpServletResponse second = send(key, createChain());
        releaseFirst.countDown();
        MockHttpServletResponse firstResponse = first.get(10, TimeUnit.SECONDS);
        MockHttpServletResponse third = send(key, createChain());

        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(firstResponse.getStatus()).isEqualTo(201);
        assertThat(third.getStatus()).isEqualTo(201);
        assertThat(third.getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(third.getContentAsString()).isEqualTo(firstResponse.getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_notes", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class)).isEqualTo(1);
    }

    @Test
    void failed_operation_rolls_back_the_effect_and_does_not_store_the_key() {
        UUID key = UUID.randomUUID();
        FilterChain failsAfterWriting = (req, res) -> {
            notes.save(new Note("recepción a medias"));
            notes.flush();
            ((HttpServletResponse) res).setStatus(500);
        };

        MockHttpServletResponse failed = send(key, failsAfterWriting);

        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_notes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class)).isZero();

        MockHttpServletResponse retry = send(key, createChain());
        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_notes", Long.class)).isEqualTo(1);
    }

    @Test
    void expired_key_is_processed_again_and_purged() {
        UUID key = UUID.randomUUID();
        send(key, createChain());

        clock.advance(Duration.ofHours(25));
        MockHttpServletResponse again = send(key, createChain());

        assertThat(again.getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_notes", Long.class)).isEqualTo(2);

        clock.advance(Duration.ofHours(25));
        int purged = new IdempotencyKeyPurger(store, new TransactionTemplate(transactionManager), clock).purge();
        assertThat(purged).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class)).isZero();
    }

    @Test
    void keys_are_scoped_by_organization() {
        UUID key = UUID.randomUUID();
        send(key, createChain());

        TenantInfo other = new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "RESIDENT");
        MockHttpServletResponse response = send(other, key, createChain());

        assertThat(response.getHeader(IdempotencyKeyFilter.REPLAYED_HEADER)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class)).isEqualTo(2);
    }

    private FilterChain createChain() {
        return (req, res) -> {
            notes.save(new Note("recepción"));
            created(res);
        };
    }

    private static void created(jakarta.servlet.ServletResponse res) throws java.io.IOException {
        HttpServletResponse http = (HttpServletResponse) res;
        http.setStatus(201);
        http.setContentType("application/json");
        http.getOutputStream().write(("{\"receptionId\":\"" + UUID.randomUUID() + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletResponse send(UUID key, FilterChain chain) {
        return send(TENANT, key, chain);
    }

    private MockHttpServletResponse send(TenantInfo tenant, UUID key, FilterChain chain) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/receptions");
        request.addHeader(IdempotencyKeyFilter.HEADER, key.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        TenantContext.runAs(tenant, () -> {
            try {
                filter.doFilter(request, response, chain);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        return response;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
