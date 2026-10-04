package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.web.servlet.MvcResult;
import pe.buildshield.core.organization.domain.model.UnitOfMeasure;
import pe.buildshield.core.organization.domain.model.WarehouseType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    // ---------- US15 Almacenes ----------

    @Cuando("registro el almacén {string} de tipo {string} en {string}")
    public void registerWarehouse(String name, String type, String address) throws Exception {
        session.send(post("/api/v1/warehouses"), Map.of("name", name, "type", warehouseType(type), "address", address));
        if (session.status() == 201) {
            session.remember(name);
        }
    }

    @Dado("que registré el almacén {string}")
    public void warehouseRegistered(String name) throws Exception {
        registerWarehouse(name, "almacén", "Av. Argentina 2500, Callao");
        session.expectStatus(201);
    }

    @Cuando("desactivo el almacén {string}")
    public void deactivateWarehouse(String name) throws Exception {
        session.send(patch("/api/v1/warehouses/" + session.id(name)), Map.of("active", false));
    }

    @Dado("que desactivé el almacén {string}")
    public void warehouseDeactivated(String name) throws Exception {
        deactivateWarehouse(name);
        session.expectStatus(200);
    }

    @Cuando("reactivo el almacén {string}")
    public void reactivateWarehouse(String name) throws Exception {
        session.send(patch("/api/v1/warehouses/" + session.id(name)), Map.of("active", true));
    }

    @Entonces("el almacén {string} está activo")
    public void warehouseIsActive(String name) throws Exception {
        assertThat(warehouse(name).path("active").asBoolean()).isTrue();
    }

    @Entonces("el almacén {string} está inactivo")
    public void warehouseIsInactive(String name) throws Exception {
        assertThat(warehouse(name).path("active").asBoolean()).isFalse();
    }

    @Cuando("consulto el almacén {string}")
    public void consultWarehouse(String name) throws Exception {
        session.send(get("/api/v1/warehouses/" + session.id(name)), null);
    }

    // ---------- US17 Asignaciones y visibilidad ----------

    @Cuando("asigno a {string} a la obra {string}")
    public void assignToWorksite(String email, String worksite) throws Exception {
        assign(email, "WORKSITE", worksite);
    }

    @Cuando("asigno a {string} al almacén {string}")
    public void assignToWarehouse(String email, String warehouse) throws Exception {
        assign(email, "WAREHOUSE", warehouse);
    }

    @Dado("que asigné a {string} a la obra {string}")
    public void assignedToWorksite(String email, String worksite) throws Exception {
        assignToWorksite(email, worksite);
        session.expectStatus(201);
    }

    @Dado("que asigné a {string} al almacén {string}")
    public void assignedToWarehouse(String email, String warehouse) throws Exception {
        assignToWarehouse(email, warehouse);
        session.expectStatus(201);
    }

    @Cuando("termino la asignación de {string} a la obra {string}")
    public void endAssignment(String email, String worksite) throws Exception {
        JsonNode assignment = findAssignment(email, worksite);
        assertThat(assignment).as("asignación de %s a %s", email, worksite).isNotNull();
        session.send(patch("/api/v1/assignments/" + assignment.path("id").asText()), Map.of("active", false));
    }

    @Entonces("las asignaciones incluyen la de {string} al almacén {string}")
    public void assignmentsIncludeWarehouse(String email, String warehouse) throws Exception {
        assertThat(findAssignment(email, warehouse)).as("asignación de %s a %s", email, warehouse).isNotNull();
    }

    @Entonces("las asignaciones incluyen la de {string} a la obra {string} ya terminada")
    public void assignmentsIncludeEnded(String email, String worksite) throws Exception {
        JsonNode assignment = findAssignment(email, worksite);
        assertThat(assignment).as("asignación de %s a %s", email, worksite).isNotNull();
        assertThat(assignment.path("active").asBoolean()).isFalse();
        assertThat(assignment.path("endedAt").isNull()).isFalse();
    }

    @Cuando("consulto las obras")
    public void listWorksites() throws Exception {
        session.send(get("/api/v1/worksites"), null);
    }

    @Cuando("consulto los almacenes")
    public void listWarehouses() throws Exception {
        session.send(get("/api/v1/warehouses"), null);
    }

    @Entonces("la lista contiene exactamente:")
    public void listContainsExactly(List<String> names) throws Exception {
        session.expectStatus(200);
        List<String> inList = new ArrayList<>();
        session.body().forEach(item -> inList.add(item.path("name").asText()));
        assertThat(inList).containsExactlyInAnyOrderElementsOf(names);
    }

    @Entonces("la lista está vacía")
    public void listIsEmpty() throws Exception {
        session.expectStatus(200);
        assertThat(session.body().size()).isZero();
    }

    private void assign(String email, String siteType, String site) throws Exception {
        session.send(post("/api/v1/assignments"), Map.of("userId", session.id(email).toString(), "siteType", siteType,
                "siteId", session.id(site).toString()));
    }

    /** Busca con la sesión actual, sin cambiar la última respuesta. */
    private JsonNode findAssignment(String email, String site) throws Exception {
        MvcResult response = session.perform(get("/api/v1/assignments"), null, session.accessToken());
        for (JsonNode assignment : session.read(response)) {
            if (assignment.path("userId").asText().equals(session.id(email).toString())
                    && assignment.path("siteId").asText().equals(session.id(site).toString())) {
                return assignment;
            }
        }
        return null;
    }

    private JsonNode warehouse(String name) throws Exception {
        MvcResult response = session.perform(get("/api/v1/warehouses/" + session.id(name)), null, session.accessToken());
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        return session.read(response);
    }

    /** "almacén" → WAREHOUSE; "centro de acopio" → COLLECTION_CENTER. */
    private static String warehouseType(String displayName) {
        return Arrays.stream(WarehouseType.values())
                .filter(type -> type.displayName().equalsIgnoreCase(displayName))
                .map(WarehouseType::name)
                .findFirst()
                .orElse(displayName);
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
