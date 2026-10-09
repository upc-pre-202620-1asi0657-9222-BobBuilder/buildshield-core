package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.buildshield.core.acceptance.ApiRequest.get;
import static pe.buildshield.core.acceptance.ApiRequest.post;
import static pe.buildshield.core.acceptance.ApiRequest.put;

/** Pasos de US35 y US37 (módulo reception). */
public class ReceptionSteps {

    @Autowired
    ScenarioSession session;

    @Autowired
    JdbcTemplate jdbc;

    // ---------- US35 Recepción y cotejo ----------

    @Cuando("abro la recepción {string} del despacho {string}")
    public void start(String name, String dispatch) throws Exception {
        session.send(post("/api/v1/receptions"), Map.of("dispatchId", session.id(dispatch).toString()));
        if (session.status() == 201) {
            session.remember(name);
        }
    }

    @Dado("que abrí la recepción {string} del despacho {string}")
    public void started(String name, String dispatch) throws Exception {
        start(name, dispatch);
        session.expectStatus(201);
    }

    @Cuando("registro que en la recepción {string} llegaron {string} del material {string}")
    public void record(String name, String quantity, String sku) throws Exception {
        UUID material = session.id(sku.toUpperCase());
        String lineId = null;
        for (JsonNode line : reception(name).path("lines")) {
            if (line.path("materialId").asText().equals(material.toString())) {
                lineId = line.path("id").asText();
            }
        }
        assertThat(lineId).as("línea de %s en la recepción", sku).isNotNull();
        session.send(put("/api/v1/receptions/" + session.id(name) + "/lines/" + lineId),
                Map.of("receivedQty", new BigDecimal(quantity)));
    }

    @Dado("que registré que en la recepción {string} llegaron {string} del material {string}")
    public void recorded(String name, String quantity, String sku) throws Exception {
        record(name, quantity, sku);
        session.expectStatus(200);
    }

    @Cuando("consulto el cotejo de la recepción {string}")
    public void comparison(String name) throws Exception {
        session.send(get("/api/v1/receptions/" + session.id(name) + "/comparison"), null);
    }

    /** Columnas: material, solicitado, despachado, recibido, diferencia, merma, dentro (sí/no). */
    @Entonces("el cotejo muestra:")
    public void comparisonShows(DataTable expected) throws Exception {
        session.expectStatus(200);
        JsonNode lines = session.body().path("lines");
        List<Map<String, String>> rows = expected.asMaps();
        assertThat(lines.size()).isEqualTo(rows.size());
        for (Map<String, String> row : rows) {
            JsonNode line = null;
            for (JsonNode candidate : lines) {
                if (candidate.path("sku").asText().equalsIgnoreCase(row.get("material"))) {
                    line = candidate;
                }
            }
            assertThat(line).as("cotejo de %s", row.get("material")).isNotNull();
            assertThat(line.path("requested").decimalValue()).isEqualByComparingTo(row.get("solicitado"));
            assertThat(line.path("dispatched").decimalValue()).isEqualByComparingTo(row.get("despachado"));
            assertThat(line.path("received").decimalValue()).isEqualByComparingTo(row.get("recibido"));
            assertThat(line.path("difference").decimalValue()).isEqualByComparingTo(row.get("diferencia"));
            assertThat(line.path("shrinkagePercent").decimalValue()).isEqualByComparingTo(row.get("merma"));
            assertThat(line.path("withinTolerance").asBoolean()).isEqualTo("sí".equals(row.get("dentro")));
        }
    }

    @Entonces("el cotejo está completo y {string} dentro de la tolerancia")
    public void comparisonSummary(String within) throws Exception {
        assertThat(session.body().path("complete").asBoolean()).isTrue();
        assertThat(session.body().path("withinTolerance").asBoolean()).isEqualTo("sí".equals(within));
    }

    // ---------- US37 Conformidad ----------

    @Cuando("confirmo la recepción {string}")
    public void confirm(String name) throws Exception {
        session.send(post("/api/v1/receptions/" + session.id(name) + "/confirm"), null);
    }

    @Cuando("confirmo la recepción {string} con la clave {string}")
    public void confirmWithKey(String name, String key) throws Exception {
        session.send(post("/api/v1/receptions/" + session.id(name) + "/confirm").idempotencyKey(session.key(key)), null);
    }

    @Dado("que confirmé la recepción {string} con la clave {string}")
    public void confirmedWithKey(String name, String key) throws Exception {
        confirmWithKey(name, key);
        session.expectStatus(200);
    }

    @Entonces("la respuesta es la misma confirmación reproducida")
    public void replayed() {
        session.expectStatus(200);
        assertThat(session.last().replayed()).isEqualTo("true");
    }

    @Entonces("la recepción {string} está en estado {string}")
    public void receptionIsIn(String name, String statusLabel) throws Exception {
        assertThat(reception(name).path("statusLabel").asText()).isEqualTo(statusLabel);
    }

    @Entonces("el stock del material {string} en la obra {string} es {string}")
    public void worksiteStockIs(String sku, String worksite, String expected) {
        List<BigDecimal> found = jdbc.queryForList("""
                SELECT available_qty FROM inventory.stock_items WHERE location_id = ? AND material_id = ?""",
                BigDecimal.class, session.id(worksite), session.id(sku.toUpperCase()));
        BigDecimal stock = found.isEmpty() ? BigDecimal.ZERO : found.get(0);
        assertThat(stock).isEqualByComparingTo(expected);
    }

    private JsonNode reception(String name) throws Exception {
        ApiResponse response = session.perform(get("/api/v1/receptions/" + session.id(name)), null, session.accessToken());
        assertThat(response.status()).as("consultar recepción %s: %s", name, response.body()).isEqualTo(200);
        return session.read(response);
    }
}
