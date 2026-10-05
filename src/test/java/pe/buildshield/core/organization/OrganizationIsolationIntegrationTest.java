package pe.buildshield.core.organization;

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
import pe.buildshield.core.support.CoreIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Aislamiento entre organizaciones: el administrador de la organización B intenta leer, modificar o
 * referenciar cada recurso de la organización A en los cuatro endpoints del módulo, y revisa que
 * ninguna lista le muestre datos de A. Se exige 100 % de intentos denegados y que A quede intacta.
 */
@CoreIntegrationTest
@AutoConfigureMockMvc
class OrganizationIsolationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Attempt> attempts = new ArrayList<>();

    private String adminA;
    private String adminB;
    private UUID worksiteA;
    private UUID warehouseA;
    private UUID materialA;
    private UUID assignmentA;
    private UUID siteManagerA;
    private UUID worksiteB;
    private UUID warehouseB;
    private UUID siteManagerB;
    private UUID warehouseManagerB;

    @BeforeEach
    void twoOrganizationsWithTheirOwnData() throws Exception {
        pe.buildshield.core.support.TestDatabase.reset(jdbc, """
                TRUNCATE organization.staff_assignments, organization.materials, organization.warehouses,
                         organization.worksites, iam.refresh_tokens, iam.password_reset_tokens, iam.users,
                         organization.organizations CASCADE""");

        adminA = signUpAndSignIn("20123456789", "ana@andina.pe");
        worksiteA = create(adminA, "/api/v1/worksites", worksite("Torre Norte A"));
        warehouseA = create(adminA, "/api/v1/warehouses", warehouse("Central A"));
        materialA = create(adminA, "/api/v1/materials", material("CEM-001"));
        siteManagerA = create(adminA, "/api/v1/users", user("jorge@andina.pe", "SITE_MANAGER"));
        assignmentA = create(adminA, "/api/v1/assignments",
                Map.of("userId", siteManagerA, "siteType", "WORKSITE", "siteId", worksiteA));

        adminB = signUpAndSignIn("20999999991", "luis@otra.pe");
        worksiteB = create(adminB, "/api/v1/worksites", worksite("Torre B"));
        warehouseB = create(adminB, "/api/v1/warehouses", warehouse("Central B"));
        siteManagerB = create(adminB, "/api/v1/users", user("pedro@otra.pe", "SITE_MANAGER"));
        warehouseManagerB = create(adminB, "/api/v1/users", user("maria@otra.pe", "WAREHOUSE_MANAGER"));
    }

    /** Control: los recursos existen y su propio administrador los ve; así el 404 de B se debe al aislamiento. */
    @Test
    void the_owner_organization_reaches_its_own_resources() throws Exception {
        for (String path : List.of("/api/v1/worksites/" + worksiteA, "/api/v1/warehouses/" + warehouseA,
                "/api/v1/materials/" + materialA, "/api/v1/assignments/" + assignmentA)) {
            assertThat(perform(get(path), null, adminA).getResponse().getStatus()).as(path).isEqualTo(200);
        }
        assertThat(perform(get("/api/v1/worksites"), null, adminA).getResponse().getContentAsString())
                .contains(worksiteA.toString()).doesNotContain(worksiteB.toString());
    }

    @Test
    void every_attempt_to_reach_another_organization_is_denied() throws Exception {
        // Lectura y modificación directa de cada recurso de A.
        attemptItem("GET obra de A", get("/api/v1/worksites/" + worksiteA), null);
        attemptItem("PATCH obra de A", patch("/api/v1/worksites/" + worksiteA), Map.of("name", "Hackeada"));
        attemptItem("GET almacén de A", get("/api/v1/warehouses/" + warehouseA), null);
        attemptItem("PATCH almacén de A", patch("/api/v1/warehouses/" + warehouseA), Map.of("active", false));
        attemptItem("GET material de A", get("/api/v1/materials/" + materialA), null);
        attemptItem("PATCH material de A", patch("/api/v1/materials/" + materialA), Map.of("wasteTolerancePercent", 99));
        attemptItem("GET asignación de A", get("/api/v1/assignments/" + assignmentA), null);
        attemptItem("PATCH asignación de A", patch("/api/v1/assignments/" + assignmentA), Map.of("active", false));

        // Referencias cruzadas al crear asignaciones.
        attemptItem("asignar usuario de A a obra de B", post("/api/v1/assignments"),
                Map.of("userId", siteManagerA, "siteType", "WORKSITE", "siteId", worksiteB));
        attemptItem("asignar usuario de B a obra de A", post("/api/v1/assignments"),
                Map.of("userId", siteManagerB, "siteType", "WORKSITE", "siteId", worksiteA));
        attemptItem("asignar usuario de B a almacén de A", post("/api/v1/assignments"),
                Map.of("userId", warehouseManagerB, "siteType", "WAREHOUSE", "siteId", warehouseA));

        // Listados: no deben contener ningún id de A.
        List<String> idsOfA = List.of(worksiteA, warehouseA, materialA, assignmentA, siteManagerA).stream()
                .map(UUID::toString).toList();
        for (String list : List.of("/api/v1/worksites", "/api/v1/warehouses", "/api/v1/materials",
                "/api/v1/assignments", "/api/v1/users")) {
            MvcResult response = perform(get(list), null, adminB);
            String body = response.getResponse().getContentAsString();
            boolean leaks = idsOfA.stream().anyMatch(body::contains);
            attempts.add(new Attempt("listar " + list, response.getResponse().getStatus(), !leaks));
        }

        long denied = attempts.stream().filter(Attempt::denied).count();
        double percent = 100.0 * denied / attempts.size();
        System.out.printf("%nAISLAMIENTO ENTRE ORGANIZACIONES: %d de %d intentos denegados (%.1f %%)%n",
                denied, attempts.size(), percent);
        attempts.forEach(attempt -> System.out.printf("  %-38s -> %d %s%n", attempt.description(), attempt.status(),
                attempt.denied() ? "denegado" : "FILTRADO"));

        assertThat(attempts).hasSize(16);
        assertThat(attempts).filteredOn(attempt -> !attempt.denied()).as("intentos que alcanzaron datos de A").isEmpty();
        assertThat(percent).isEqualTo(100.0);
        organizationAIsUntouched();
    }

    private void organizationAIsUntouched() {
        assertThat(jdbc.queryForObject("SELECT name FROM organization.worksites WHERE id = ?", String.class, worksiteA))
                .isEqualTo("Torre Norte A");
        assertThat(jdbc.queryForObject("SELECT active FROM organization.warehouses WHERE id = ?", Boolean.class, warehouseA))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT waste_tolerance_percent FROM organization.materials WHERE id = ?",
                java.math.BigDecimal.class, materialA)).isEqualByComparingTo("2.5");
        assertThat(jdbc.queryForObject("SELECT ended_at IS NULL FROM organization.staff_assignments WHERE id = ?",
                Boolean.class, assignmentA)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM organization.staff_assignments
                WHERE worksite_id = ? OR warehouse_id = ? OR user_id = ?""", Long.class, worksiteA, warehouseA, siteManagerA))
                .as("solo la asignación propia de A").isEqualTo(1);
    }

    /** Un intento sobre un recurso de A está denegado si responde 404, como si no existiera. */
    private void attemptItem(String description, MockHttpServletRequestBuilder request, Object body) throws Exception {
        int status = perform(request, body, adminB).getResponse().getStatus();
        attempts.add(new Attempt(description, status, status == 404));
    }

    private String signUpAndSignIn(String ruc, String email) throws Exception {
        assertThat(perform(post("/api/v1/auth/sign-up"), Map.of("ruc", ruc, "legalName", "Constructora " + ruc,
                "adminFullName", "Admin", "adminEmail", email, "password", "Segura123"), null).getResponse().getStatus())
                .isEqualTo(201);
        MvcResult signIn = perform(post("/api/v1/auth/sign-in"), Map.of("email", email, "password", "Segura123"), null);
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

    private static Map<String, Object> material(String sku) {
        return Map.of("sku", sku, "name", "Cemento", "unit", "BAG", "wasteTolerancePercent", 2.5);
    }

    private static Map<String, Object> user(String email, String role) {
        return Map.of("fullName", "Encargado", "email", email, "role", role, "password", "Encargado123");
    }

    private record Attempt(String description, int status, boolean denied) {
    }
}
