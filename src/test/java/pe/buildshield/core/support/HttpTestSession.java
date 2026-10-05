package pe.buildshield.core.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Todas las peticiones pasan por un servidor HTTP real; cada intención nueva obtiene su propia clave. */
public class HttpTestSession {
    public static final String PASSWORD = "Segura123";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    public HttpTestSession(int port, ObjectMapper json) { this.baseUrl = "http://127.0.0.1:" + port; this.json = json; }

    public Reply call(String method, String path, Object body, String bearer) throws Exception {
        UUID key = method.equals("POST") && (path.startsWith("/api/v1/orders") || path.equals("/api/v1/stock/entries"))
                ? UUID.randomUUID() : null;
        return call(method, path, body, bearer, key);
    }
    public Reply call(String method, String path, Object body, String bearer, UUID key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (key != null) request.header("Idempotency-Key", key.toString());
        request.header("Content-Type", "application/json");
        String payload = body == null ? "" : body instanceof String text ? text : json.writeValueAsString(body);
        var response = http.send(request.method(method, HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode data = response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
        return new Reply(response.statusCode(), response.body(), data, response.headers());
    }
    public Tenant tenant() throws Exception {
        String email = UUID.randomUUID() + "@buildshield.test";
        String ruc = "20" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
        Reply created = call("POST", "/api/v1/auth/sign-up", Map.of("ruc", ruc, "legalName", "Constructora de prueba",
                "adminFullName", "Administrador", "adminEmail", email, "password", PASSWORD), null);
        assertThat(created.status()).as(created.text()).isEqualTo(201);
        return new Tenant(signIn(email), UUID.fromString(created.data().path("organizationId").asText()),
                UUID.fromString(created.data().path("administratorId").asText()), email);
    }
    public String signIn(String email) throws Exception {
        Reply session = call("POST", "/api/v1/auth/sign-in", Map.of("email", email, "password", PASSWORD), null);
        assertThat(session.status()).as(session.text()).isEqualTo(200);
        return session.data().path("accessToken").asText();
    }
    public UUID create(String path, Object body, String token) throws Exception {
        Reply result = call("POST", path, body, token);
        assertThat(result.status()).as(path + ": " + result.text()).isEqualTo(201);
        return UUID.fromString(result.data().path("id").asText());
    }
    public Fixture fixture() throws Exception {
        Tenant tenant = tenant();
        UUID site = create("/api/v1/worksites", Map.of("name", "Obra", "address", "Av. 1", "district", "Lince",
                "city", "Lima", "startDate", "2026-11-01"), tenant.token());
        UUID warehouse = create("/api/v1/warehouses", Map.of("name", "Central", "type", "WAREHOUSE", "address", "Av. 1"), tenant.token());
        UUID material = create("/api/v1/materials", Map.of("sku", "CEM-001", "name", "Cemento", "unit", "BAG",
                "wasteTolerancePercent", 2.5), tenant.token());
        String siteEmail = UUID.randomUUID() + "@buildshield.test";
        UUID siteManager = create("/api/v1/users", Map.of("fullName", "Encargado", "email", siteEmail,
                "role", "SITE_MANAGER", "password", PASSWORD), tenant.token());
        create("/api/v1/assignments", Map.of("userId", siteManager, "siteType", "WORKSITE", "siteId", site), tenant.token());
        return new Fixture(tenant, site, warehouse, material, siteManager, signIn(siteEmail));
    }
    public record Reply(int status, String text, JsonNode data, HttpHeaders headers) { }
    public record Tenant(String token, UUID organizationId, UUID userId, String email) { }
    public record Fixture(Tenant tenant, UUID worksite, UUID warehouse, UUID material, UUID siteManager, String siteToken) {
        public Map<String, Object> entry(int quantity) {
            return Map.of("warehouseId", warehouse, "materialId", material, "quantity", quantity);
        }
        public Map<String, Object> order() {
            return Map.of("worksiteId", worksite, "warehouseId", warehouse,
                    "lines", List.of(Map.of("materialId", material, "quantity", 5)));
        }
    }
}
