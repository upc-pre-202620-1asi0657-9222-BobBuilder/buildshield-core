package pe.buildshield.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.buildshield.core.reception.application.ReceptionService;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.support.ContainersConfig;
import pe.buildshield.core.support.HttpTestSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 de punta a punta por HTTP real con PostgreSQL: reserva al aprobar, despacho, transportista,
 * pesaje, salida, recepción, cotejo y conformidad. Mide además QAS02 (aprobaciones y despachos
 * simultáneos no dejan stock negativo ni asignan de más), QAS10 (la conformidad se aplica una sola vez) y
 * que el cotejo no hace N+1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ContainersConfig.class)
class DispatchReceptionFlowIT {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptionService receptionService;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    HttpTestSession api;
    HttpTestSession.Fixture f;

    @BeforeEach
    void prepare() throws Exception {
        api = new HttpTestSession(port, json);
        f = api.fixture();
    }

    @Test
    void full_flow_reserves_dispatches_receives_and_confirms_once() throws Exception {
        entry(100);
        UUID order = order(50);
        UUID line = orderLine(order);
        assertThat(api.call("POST", "/api/v1/orders/" + order + "/approve", null, admin()).status()).isEqualTo(200);
        assertThat(level("available_qty", f.warehouse())).isEqualByComparingTo("50");
        assertThat(level("reserved_qty", f.warehouse())).isEqualByComparingTo("50");

        var created = api.call("POST", "/api/v1/dispatches", dispatchBody(order, line, 30), admin());
        assertThat(created.status()).as(created.text()).isEqualTo(201);
        UUID dispatch = UUID.fromString(created.data().path("id").asText());
        assertThat(created.data().path("type").asText()).isEqualTo("PARTIAL");
        assertThat(created.data().path("manifestCode").asText()).startsWith("MAN-");
        assertThat(orderJson(order).path("status").asText()).isEqualTo("PARTIALLY_FULFILLED");

        assertThat(api.call("POST", "/api/v1/dispatches/" + dispatch + "/depart", null, admin()).text())
                .contains("CARRIER_REQUIRED");
        assertThat(api.call("PATCH", "/api/v1/dispatches/" + dispatch + "/carrier", Map.of("carrierName", "Transportes Rímac",
                "carrierDocument", "20555666777", "plate", "ABC-123"), admin()).status()).isEqualTo(200);
        assertThat(api.call("POST", "/api/v1/dispatches/" + dispatch + "/departure-weighing", Map.of("grossKg", 2550.5,
                "tareKg", 1050.5, "ticketPhotoUrl", "https://evidencias.buildshield.pe/t-1.jpg"), admin()).status()).isEqualTo(200);
        assertThat(api.call("POST", "/api/v1/dispatches/" + dispatch + "/departure-weighing", Map.of("grossKg", 10,
                "tareKg", 1), admin()).text()).contains("DEPARTURE_WEIGHING_ALREADY_RECORDED");
        UUID departKey = UUID.randomUUID();
        var departed = api.call("POST", "/api/v1/dispatches/" + dispatch + "/depart", null, admin(), departKey);
        assertThat(departed.status()).as(departed.text()).isEqualTo(200);
        assertThat(departed.data().path("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(api.call("POST", "/api/v1/dispatches/" + dispatch + "/depart", null, admin(), departKey).text())
                .isEqualTo(departed.text());
        assertThat(level("available_qty", f.warehouse())).isEqualByComparingTo("50");
        assertThat(level("reserved_qty", f.warehouse())).isEqualByComparingTo("20");

        var manifest = api.call("GET", "/api/v1/dispatches/" + dispatch + "/manifest", null, f.siteToken());
        assertThat(manifest.status()).isEqualTo(200);
        assertThat(manifest.data().path("qrContent").asText()).isEqualTo(created.data().path("manifestCode").asText());
        assertThat(manifest.data().path("lines").get(0).path("sku").asText()).isEqualTo("CEM-001");
        assertThat(manifest.data().path("qrCodePngBase64").asText()).isNotBlank();

        UUID reception = startReception(dispatch);
        assertThat(api.call("POST", "/api/v1/receptions", Map.of("dispatchId", dispatch), f.siteToken()).text())
                .contains("RECEPTION_ALREADY_EXISTS", reception.toString());
        UUID receptionLine = receptionLine(reception);
        assertThat(api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, f.siteToken()).text())
                .contains("RECEPTION_INCOMPLETE");
        assertThat(api.call("PUT", "/api/v1/receptions/" + reception + "/lines/" + receptionLine,
                Map.of("receivedQty", 30.5), f.siteToken()).text()).contains("RECEIVED_EXCEEDS_DISPATCHED");
        assertThat(api.call("PUT", "/api/v1/receptions/" + reception + "/lines/" + receptionLine,
                Map.of("receivedQty", 29.5), f.siteToken()).status()).isEqualTo(200);
        var comparison = api.call("GET", "/api/v1/receptions/" + reception + "/comparison", null, f.siteToken());
        JsonNode compared = comparison.data().path("lines").get(0);
        assertThat(compared.path("requested").decimalValue()).isEqualByComparingTo("50");
        assertThat(compared.path("dispatched").decimalValue()).isEqualByComparingTo("30");
        assertThat(compared.path("received").decimalValue()).isEqualByComparingTo("29.5");
        assertThat(compared.path("shrinkagePercent").decimalValue()).isEqualByComparingTo("1.67");
        assertThat(compared.path("withinTolerance").asBoolean()).isTrue();

        UUID confirmKey = UUID.randomUUID();
        var confirmed = api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, f.siteToken(), confirmKey);
        assertThat(confirmed.status()).as(confirmed.text()).isEqualTo(200);
        var replay = api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, f.siteToken(), confirmKey);
        assertThat(replay.text()).isEqualTo(confirmed.text());
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        var second = api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, f.siteToken(), UUID.randomUUID());
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.text()).contains("RECEPTION_ALREADY_CONFIRMED");

        assertThat(level("available_qty", f.worksite())).isEqualByComparingTo("29.5");
        JsonNode orderLine = orderJson(order).path("lines").get(0);
        assertThat(orderLine.path("dispatched").decimalValue()).isEqualByComparingTo("30");
        assertThat(orderLine.path("received").decimalValue()).isEqualByComparingTo("29.5");
        assertThat(orderLine.path("pending").decimalValue()).isEqualByComparingTo("20");
        assertThat(api.call("GET", "/api/v1/dispatches/" + dispatch, null, admin()).data().path("status").asText())
                .isEqualTo("RECEIVED");
        for (String action : List.of("STOCK_RESERVED", "DISPATCH_CREATED", "DISPATCH_CARRIER_ASSIGNED",
                "DEPARTURE_WEIGHING_RECORDED", "DISPATCH_DEPARTED", "STOCK_DISPATCHED", "RECEPTION_STARTED",
                "RECEPTION_CONFIRMED")) {
            assertThat(events(action)).as(action).isEqualTo(1);
        }
        var history = api.call("GET", "/api/v1/audit/events?resourceType=DISPATCH&resourceId=" + dispatch, null, admin());
        assertThat(history.data()).hasSize(4);
    }

    @RepeatedTest(5)
    void simultaneous_dispatches_of_one_order_never_over_allocate() throws Exception {
        entry(100);
        UUID order = order(50);
        UUID line = orderLine(order);
        api.call("POST", "/api/v1/orders/" + order + "/approve", null, admin());

        List<HttpTestSession.Reply> results = simultaneously(
                () -> api.call("POST", "/api/v1/dispatches", dispatchBody(order, line, 30), admin()),
                () -> api.call("POST", "/api/v1/dispatches", dispatchBody(order, line, 30), admin()));

        assertThat(results).extracting(HttpTestSession.Reply::status).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT dispatched_qty FROM ordering.order_lines WHERE id = ?", BigDecimal.class,
                line)).isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispatch.dispatches WHERE order_id = ?", Long.class, order))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    void simultaneous_approvals_over_the_same_stock_never_go_negative() throws Exception {
        entry(10);
        UUID first = order(7);
        UUID second = order(7);

        List<HttpTestSession.Reply> results = simultaneously(
                () -> api.call("POST", "/api/v1/orders/" + first + "/approve", null, admin()),
                () -> api.call("POST", "/api/v1/orders/" + second + "/approve", null, admin()));

        assertThat(results).extracting(HttpTestSession.Reply::status).containsExactlyInAnyOrder(200, 409);
        assertThat(results).filteredOn(reply -> reply.status() == 409).singleElement()
                .satisfies(reply -> assertThat(reply.text()).contains("INSUFFICIENT_STOCK"));
        assertThat(level("available_qty", f.warehouse())).isEqualByComparingTo("3");
        assertThat(level("reserved_qty", f.warehouse())).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordering.orders WHERE id IN (?, ?) AND status = 'IN_REVIEW'",
                Long.class, first, second)).isEqualTo(1);
    }

    @RepeatedTest(5)
    void simultaneous_confirmations_with_different_keys_apply_once() throws Exception {
        UUID reception = receptionWithEverythingRecorded(30, "28");

        List<HttpTestSession.Reply> results = simultaneously(
                () -> api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, f.siteToken()),
                () -> api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, f.siteToken()));

        assertThat(results).extracting(HttpTestSession.Reply::status).containsExactlyInAnyOrder(200, 409);
        assertThat(level("available_qty", f.worksite())).isEqualByComparingTo("28");
        assertThat(events("RECEPTION_CONFIRMED")).isEqualTo(1);
    }

    @Test
    void another_organization_reaches_no_dispatch_or_reception() throws Exception {
        UUID reception = receptionWithEverythingRecorded(10, "10");
        UUID dispatch = UUID.fromString(api.call("GET", "/api/v1/receptions/" + reception, null, admin())
                .data().path("dispatchId").asText());
        var other = api.fixture();
        String intruder = other.tenant().token();
        List<Integer> statuses = new ArrayList<>();
        statuses.add(api.call("GET", "/api/v1/dispatches/" + dispatch, null, intruder).status());
        statuses.add(api.call("GET", "/api/v1/dispatches/" + dispatch + "/manifest", null, intruder).status());
        statuses.add(api.call("POST", "/api/v1/dispatches/" + dispatch + "/depart", null, intruder).status());
        statuses.add(api.call("PATCH", "/api/v1/dispatches/" + dispatch + "/carrier", Map.of("carrierName", "X",
                "carrierDocument", "12345678", "plate", "ABC-123"), intruder).status());
        statuses.add(api.call("GET", "/api/v1/receptions/" + reception, null, intruder).status());
        statuses.add(api.call("GET", "/api/v1/receptions/" + reception + "/comparison", null, intruder).status());
        statuses.add(api.call("POST", "/api/v1/receptions/" + reception + "/confirm", null, intruder).status());
        statuses.add(api.call("POST", "/api/v1/receptions", Map.of("dispatchId", dispatch), other.siteToken()).status());

        assertThat(statuses).containsOnly(404);
        assertThat(api.call("GET", "/api/v1/dispatches", null, intruder).data()).isEmpty();
    }

    @Test
    void comparison_uses_the_same_number_of_queries_for_one_or_many_materials() throws Exception {
        UUID oneLine = receptionWithEverythingRecorded(10, "10");
        UUID manyLines = receptionWithLines(4);

        long one = statementsOfComparison(oneLine);
        long many = statementsOfComparison(manyLines);

        assertThat(many).as("consultas con 4 materiales").isEqualTo(one);
        assertThat(one).as("consultas del cotejo").isLessThanOrEqualTo(5);
    }

    // ---------- apoyo ----------

    private String admin() {
        return f.tenant().token();
    }

    private void entry(int quantity) throws Exception {
        assertThat(api.call("POST", "/api/v1/stock/entries", f.entry(quantity), admin()).status()).isEqualTo(201);
    }

    private UUID order(int quantity) throws Exception {
        return api.create("/api/v1/orders", Map.of("worksiteId", f.worksite(), "warehouseId", f.warehouse(),
                "lines", List.of(Map.of("materialId", f.material(), "quantity", quantity))), f.siteToken());
    }

    private JsonNode orderJson(UUID order) throws Exception {
        return api.call("GET", "/api/v1/orders/" + order, null, admin()).data();
    }

    private UUID orderLine(UUID order) throws Exception {
        return UUID.fromString(orderJson(order).path("lines").get(0).path("id").asText());
    }

    private static Map<String, Object> dispatchBody(UUID order, UUID line, int quantity) {
        return Map.of("orderId", order, "lines", List.of(Map.of("orderLineId", line, "quantity", quantity)));
    }

    private UUID startReception(UUID dispatch) throws Exception {
        return api.create("/api/v1/receptions", Map.of("dispatchId", dispatch), f.siteToken());
    }

    private UUID receptionLine(UUID reception) throws Exception {
        return UUID.fromString(api.call("GET", "/api/v1/receptions/" + reception, null, f.siteToken())
                .data().path("lines").get(0).path("id").asText());
    }

    /** Pedido aprobado, despacho en tránsito y recepción con todo lo recibido registrado. */
    private UUID receptionWithEverythingRecorded(int quantity, String received) throws Exception {
        entry(quantity);
        UUID order = order(quantity);
        UUID line = orderLine(order);
        api.call("POST", "/api/v1/orders/" + order + "/approve", null, admin());
        UUID dispatch = api.create("/api/v1/dispatches", dispatchBody(order, line, quantity), admin());
        depart(dispatch);
        UUID reception = startReception(dispatch);
        assertThat(api.call("PUT", "/api/v1/receptions/" + reception + "/lines/" + receptionLine(reception),
                Map.of("receivedQty", new BigDecimal(received)), f.siteToken()).status()).isEqualTo(200);
        return reception;
    }

    private UUID receptionWithLines(int materials) throws Exception {
        List<Map<String, Object>> orderLines = new ArrayList<>();
        for (int i = 0; i < materials; i++) {
            UUID material = api.create("/api/v1/materials", Map.of("sku", "MAT-" + i, "name", "Material " + i,
                    "unit", "BAG", "wasteTolerancePercent", 1), admin());
            api.call("POST", "/api/v1/stock/entries", Map.of("warehouseId", f.warehouse(), "materialId", material,
                    "quantity", 5), admin());
            orderLines.add(Map.of("materialId", material, "quantity", 5));
        }
        UUID order = api.create("/api/v1/orders", Map.of("worksiteId", f.worksite(), "warehouseId", f.warehouse(),
                "lines", orderLines), f.siteToken());
        assertThat(api.call("POST", "/api/v1/orders/" + order + "/approve", null, admin()).status()).isEqualTo(200);
        List<Map<String, Object>> lines = new ArrayList<>();
        orderJson(order).path("lines").forEach(line -> lines.add(Map.of("orderLineId", line.path("id").asText(),
                "quantity", 5)));
        UUID dispatch = api.create("/api/v1/dispatches", Map.of("orderId", order, "lines", lines), admin());
        depart(dispatch);
        return startReception(dispatch);
    }

    private void depart(UUID dispatch) throws Exception {
        api.call("PATCH", "/api/v1/dispatches/" + dispatch + "/carrier", Map.of("carrierName", "Transportes Rímac",
                "carrierDocument", "20555666777", "plate", "ABC-123"), admin());
        api.call("POST", "/api/v1/dispatches/" + dispatch + "/departure-weighing", Map.of("grossKg", 100, "tareKg", 40),
                admin());
        var departed = api.call("POST", "/api/v1/dispatches/" + dispatch + "/depart", null, admin());
        assertThat(departed.status()).as(departed.text()).isEqualTo(200);
    }

    private long statementsOfComparison(UUID reception) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            TenantContext.runAs(new TenantInfo(f.tenant().organizationId(), f.tenant().userId(), "ADMINISTRATOR"),
                    () -> receptionService.comparison(reception));
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @SafeVarargs
    private static List<HttpTestSession.Reply> simultaneously(Callable<HttpTestSession.Reply>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpTestSession.Reply>> futures = new ArrayList<>();
            for (Callable<HttpTestSession.Reply> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<HttpTestSession.Reply> results = new ArrayList<>();
            for (Future<HttpTestSession.Reply> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal level(String column, UUID location) {
        return jdbc.queryForObject("SELECT " + column + " FROM inventory.stock_items WHERE organization_id = ? "
                + "AND location_id = ? AND material_id = ?", BigDecimal.class, f.tenant().organizationId(), location,
                f.material());
    }

    private long events(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE organization_id = ? AND action = ?",
                Long.class, f.tenant().organizationId(), action);
    }
}
