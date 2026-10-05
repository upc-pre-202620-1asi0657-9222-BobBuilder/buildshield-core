package pe.buildshield.core.ordering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.core.support.CoreIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Aislamiento entre organizaciones en pedidos y stock: la organización B intenta ver, aprobar,
 * rechazar o referenciar pedidos, almacenes, materiales y obras de A. Se exige 100 % denegado.
 */
@CoreIntegrationTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrdersStockIsolationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Attempt> attempts = new ArrayList<>();

    private String adminA;
    private String adminB;
    private String siteManagerB;
    private UUID worksiteA;
    private UUID warehouseA;
    private UUID materialA;
    private UUID orderA;
    private UUID worksiteB;
    private UUID warehouseB;
    private UUID materialB;

    @BeforeEach
    void twoOrganizationsWithOrdersAndStock() throws Exception {
        jdbc.execute("""
                TRUNCATE ordering.order_lines, ordering.orders, inventory.stock_movements, inventory.stock_items,
                         organization.staff_assignments, organization.materials, organization.warehouses,
                         organization.worksites, iam.refresh_tokens, iam.password_reset_tokens, iam.users,
                         organization.organizations CASCADE""");

        adminA = signUpAndSignIn("20123456789", "ana@andina.pe");
        worksiteA = create(adminA, "/api/v1/worksites", worksite("Torre A"));
        warehouseA = create(adminA, "/api/v1/warehouses", warehouse("Central A"));
        materialA = create(adminA, "/api/v1/materials", material());
        UUID jorge = create(adminA, "/api/v1/users", user("jorge@andina.pe", "SITE_MANAGER"));
        create(adminA, "/api/v1/assignments", Map.of("userId", jorge, "siteType", "WORKSITE", "siteId", worksiteA));
        assertThat(perform(post("/api/v1/stock/entries"),
                Map.of("warehouseId", warehouseA, "materialId", materialA, "quantity", 100), adminA)
                .getResponse().getStatus()).isEqualTo(201);
        String jorgeToken = signIn("jorge@andina.pe", "Encargado123");
        orderA = create(jorgeToken, "/api/v1/orders", order(worksiteA, warehouseA, materialA));

        adminB = signUpAndSignIn("20999999991", "luis@otra.pe");
        worksiteB = create(adminB, "/api/v1/worksites", worksite("Torre B"));
        warehouseB = create(adminB, "/api/v1/warehouses", warehouse("Central B"));
        materialB = create(adminB, "/api/v1/materials", material());
        UUID pedro = create(adminB, "/api/v1/users", user("pedro@otra.pe", "SITE_MANAGER"));
        create(adminB, "/api/v1/assignments", Map.of("userId", pedro, "siteType", "WORKSITE", "siteId", worksiteB));
        siteManagerB = signIn("pedro@otra.pe", "Encargado123");
    }

    @Test
    void the_owner_organization_reaches_its_order_and_stock() throws Exception {
        assertThat(perform(get("/api/v1/orders/" + orderA), null, adminA).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(get("/api/v1/stock"), null, adminA).getResponse().getContentAsString())
                .contains(warehouseA.toString(), materialA.toString());
    }

    @Test
    void every_attempt_to_reach_another_organization_is_denied() throws Exception {
        attemptItem("GET pedido de A", get("/api/v1/orders/" + orderA), null, adminB);
        attemptItem("aprobar pedido de A", post("/api/v1/orders/" + orderA + "/approve"), null, adminB);
        attemptItem("rechazar pedido de A", post("/api/v1/orders/" + orderA + "/reject"), Map.of("reason", "x"), adminB);
        attemptItem("cargar stock al almacén de A", post("/api/v1/stock/entries"),
                Map.of("warehouseId", warehouseA, "materialId", materialB, "quantity", 5), adminB);
        attemptItem("cargar material de A en almacén de B", post("/api/v1/stock/entries"),
                Map.of("warehouseId", warehouseB, "materialId", materialA, "quantity", 5), adminB);
        attemptItem("pedir para la obra de A", post("/api/v1/orders"), order(worksiteA, warehouseB, materialB), siteManagerB);
        attemptItem("pedir al almacén de A", post("/api/v1/orders"), order(worksiteB, warehouseA, materialB), siteManagerB);
        attemptItem("pedir material de A", post("/api/v1/orders"), order(worksiteB, warehouseB, materialA), siteManagerB);

        List<String> idsOfA = List.of(orderA, warehouseA, materialA, worksiteA).stream().map(UUID::toString).toList();
        for (String list : List.of("/api/v1/orders", "/api/v1/stock", "/api/v1/stock?warehouseId=" + warehouseA)) {
            MvcResult response = perform(get(list), null, adminB);
            String body = response.getResponse().getContentAsString();
            attempts.add(new Attempt("listar " + list, response.getResponse().getStatus(),
                    idsOfA.stream().noneMatch(body::contains)));
        }

        long denied = attempts.stream().filter(Attempt::denied).count();
        double percent = 100.0 * denied / attempts.size();
        System.out.printf("%nAISLAMIENTO PEDIDOS Y STOCK: %d de %d intentos denegados (%.1f %%)%n",
                denied, attempts.size(), percent);
        attempts.forEach(attempt -> System.out.printf("  %-40s -> %d %s%n", attempt.description(), attempt.status(),
                attempt.denied() ? "denegado" : "FILTRADO"));

        assertThat(attempts).hasSize(11);
        assertThat(attempts).filteredOn(attempt -> !attempt.denied()).as("intentos que alcanzaron datos de A").isEmpty();
        assertThat(percent).isEqualTo(100.0);
        assertThat(jdbc.queryForObject("SELECT status FROM ordering.orders WHERE id = ?", String.class, orderA))
                .isEqualTo("REGISTERED");
        assertThat(jdbc.queryForObject("SELECT available_qty FROM inventory.stock_items WHERE location_id = ?",
                java.math.BigDecimal.class, warehouseA)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ordering.orders", Long.class)).isEqualTo(1);
    }

    private void attemptItem(String description, MockHttpServletRequestBuilder request, Object body, String token)
            throws Exception {
        int status = perform(request, body, token).getResponse().getStatus();
        attempts.add(new Attempt(description, status, status == 404));
    }

    private String signUpAndSignIn(String ruc, String email) throws Exception {
        assertThat(perform(post("/api/v1/auth/sign-up"), Map.of("ruc", ruc, "legalName", "Constructora " + ruc,
                "adminFullName", "Admin", "adminEmail", email, "password", "Segura123"), null).getResponse().getStatus())
                .isEqualTo(201);
        return signIn(email, "Segura123");
    }

    private String signIn(String email, String password) throws Exception {
        MvcResult signIn = perform(post("/api/v1/auth/sign-in"), Map.of("email", email, "password", password), null);
        assertThat(signIn.getResponse().getStatus()).isEqualTo(200);
        return read(signIn).path("accessToken").asText();
    }

    private UUID create(String token, String path, Object body) throws Exception {
        MvcResult result = perform(post(path), body, token);
        assertThat(result.getResponse().getStatus()).as("%s: %s", path, result.getResponse().getContentAsString())
                .isEqualTo(201);
        return UUID.fromString(read(result).path("id").asText());
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static Map<String, Object> worksite(String name) {
        return Map.of("name", name, "address", "Av. 1", "district", "Lince", "city", "Lima", "startDate", "2026-11-01");
    }

    private static Map<String, Object> warehouse(String name) {
        return Map.of("name", name, "type", "WAREHOUSE", "address", "Av. Argentina 2500");
    }

    private static Map<String, Object> material() {
        return Map.of("sku", "CEM-001", "name", "Cemento", "unit", "BAG", "wasteTolerancePercent", 2.5);
    }

    private static Map<String, Object> user(String email, String role) {
        return Map.of("fullName", "Encargado", "email", email, "role", role, "password", "Encargado123");
    }

    private static Map<String, Object> order(UUID worksite, UUID warehouse, UUID material) {
        return Map.of("worksiteId", worksite, "warehouseId", warehouse,
                "lines", List.of(Map.of("materialId", material, "quantity", 10)));
    }

    private record Attempt(String description, int status, boolean denied) {
    }
}
