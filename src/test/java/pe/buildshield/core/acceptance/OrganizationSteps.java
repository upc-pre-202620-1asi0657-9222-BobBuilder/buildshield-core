package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;

import pe.buildshield.core.organization.domain.model.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de las historias US14 a US17 (módulo organization). */
public class OrganizationSteps {

    @Autowired
    ScenarioSession session;

    // ---------- US14 Obras ----------

    @Cuando("registro la obra {string} en {string}, {string}, {string} del {string} al {string}")
    public void registerWorksite(String name, String address, String district, String city, String start, String end)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "address", address, "district", district,
                "city", city, "startDate", start));
        if (end != null) {
            body.put("endDate", end);
        }
        session.send(post("/api/v1/worksites"), body);
        if (session.status() == 201) {
            session.remember(name);
        }
    }

    @Cuando("registro la obra {string} en {string}, {string}, {string} desde el {string} sin fecha de fin")
    public void registerOpenWorksite(String name, String address, String district, String city, String start)
            throws Exception {
        registerWorksite(name, address, district, city, start, null);
    }

    @Dado("que registré la obra {string} del {string} al {string}")
    public void worksiteRegistered(String name, String start, String end) throws Exception {
        registerWorksite(name, "Av. Principal 100", "Miraflores", "Lima", start, end);
        session.expectStatus(201);
    }

    @Entonces("la obra {string} figura en {string}, {string}, {string} del {string} al {string}")
    public void worksiteIs(String name, String address, String district, String city, String start, String end)
            throws Exception {
        consultWorksite(name);
        session.expectStatus(200);
        JsonNode worksite = session.body();
        assertThat(worksite.path("name").asText()).isEqualTo(name);
        assertThat(worksite.path("address").asText()).isEqualTo(address);
        assertThat(worksite.path("district").asText()).isEqualTo(district);
        assertThat(worksite.path("city").asText()).isEqualTo(city);
        assertThat(worksite.path("startDate").asText()).isEqualTo(start);
        assertThat(worksite.path("endDate").asText()).isEqualTo(end);
    }

    @Cuando("cambio la fecha de fin de la obra {string} al {string}")
    public void changeEndDate(String name, String end) throws Exception {
        session.send(patch("/api/v1/worksites/" + session.id(name)), Map.of("endDate", end));
    }

    @Cuando("consulto la obra {string}")
    public void consultWorksite(String name) throws Exception {
        session.send(get("/api/v1/worksites/" + session.id(name)), null);
    }

    // ---------- US16 Catálogo de materiales ----------

    @Cuando("registro el material {string} con SKU {string}, unidad {string} y tolerancia de merma {string} %")
    public void registerMaterial(String name, String sku, String unit, String tolerance) throws Exception {
        session.send(post("/api/v1/materials"), Map.of("sku", sku, "name", name, "unit", unit(unit),
                "wasteTolerancePercent", new BigDecimal(tolerance)));
        if (session.status() == 201) {
            session.put(sku.toUpperCase(), java.util.UUID.fromString(session.body().path("id").asText()));
        }
    }

    @Dado("que registré el material {string} con SKU {string}, unidad {string} y tolerancia de merma {string} %")
    public void materialRegistered(String name, String sku, String unit, String tolerance) throws Exception {
        registerMaterial(name, sku, unit, tolerance);
        session.expectStatus(201);
    }

    @Cuando("cambio la tolerancia de merma del material {string} a {string} %")
    public void changeTolerance(String sku, String tolerance) throws Exception {
        session.send(patch("/api/v1/materials/" + session.id(sku)), Map.of("wasteTolerancePercent", new BigDecimal(tolerance)));
    }

    @Cuando("consulto el catálogo de materiales")
    public void consultCatalog() throws Exception {
        session.send(get("/api/v1/materials"), null);
    }

    @Entonces("el catálogo contiene el material {string} con tolerancia de merma {string} %")
    public void catalogContains(String sku, String tolerance) throws Exception {
        JsonNode catalog = session.body();
        if (!catalog.isArray()) {
            consultCatalog();
            session.expectStatus(200);
            catalog = session.body();
        }
        JsonNode found = null;
        for (JsonNode material : catalog) {
            if (material.path("sku").asText().equals(sku)) {
                found = material;
            }
        }
        assertThat(found).as("material %s en el catálogo", sku).isNotNull();
        assertThat(found.path("wasteTolerancePercent").decimalValue()).isEqualByComparingTo(tolerance);
    }

    /** "bolsa" → BAG; "metro cúbico" → M3. */
    private static String unit(String displayName) {
        return Arrays.stream(UnitOfMeasure.values())
                .filter(unit -> unit.displayName().equalsIgnoreCase(displayName))
                .map(UnitOfMeasure::name)
                .findFirst()
                .orElse(displayName);
    }
}
