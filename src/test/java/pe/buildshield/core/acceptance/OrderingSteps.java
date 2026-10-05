package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.buildshield.core.acceptance.ApiRequest.get;
import static pe.buildshield.core.acceptance.ApiRequest.post;

/** Pasos de las historias US18, US20 y US21 (módulo ordering). */
public class OrderingSteps {

    @Autowired
    ScenarioSession session;

    // ---------- US18 Crear pedido ----------

    @Cuando("creo el pedido {string} para la obra {string} al almacén {string} con:")
    public void placeOrder(String name, String worksite, String warehouse, DataTable lines) throws Exception {
        List<Map<String, Object>> body = new ArrayList<>();
        for (Map<String, String> row : lines.asMaps()) {
            body.add(Map.of("materialId", session.id(row.get("material").toUpperCase()).toString(),
                    "quantity", new BigDecimal(row.get("cantidad"))));
        }
        session.send(post("/api/v1/orders"), Map.of("worksiteId", session.id(worksite).toString(),
                "warehouseId", session.id(warehouse).toString(), "lines", body));
        if (session.status() == 201) {
            session.remember(name);
        }
    }

    @Dado("que creé el pedido {string} para la obra {string} al almacén {string} con:")
    public void orderPlaced(String name, String worksite, String warehouse, DataTable lines) throws Exception {
        placeOrder(name, worksite, warehouse, lines);
        session.expectStatus(201);
    }

    /** Verificación con la sesión actual, sin cambiar la última respuesta. */
    @Entonces("el pedido {string} está en estado {string}")
    public void orderIsIn(String name, String statusLabel) throws Exception {
        assertThat(order(name).path("statusLabel").asText()).isEqualTo(statusLabel);
    }

    // ---------- US20 Aprobar o rechazar ----------

    @Cuando("apruebo el pedido {string}")
    public void approve(String name) throws Exception {
        session.send(post("/api/v1/orders/" + session.id(name) + "/approve"), null);
    }

    @Cuando("rechazo el pedido {string} con el motivo {string}")
    public void reject(String name, String reason) throws Exception {
        session.send(post("/api/v1/orders/" + session.id(name) + "/reject"), Map.of("reason", reason));
    }

    @Dado("que rechacé el pedido {string} con el motivo {string}")
    public void rejected(String name, String reason) throws Exception {
        reject(name, reason);
        session.expectStatus(200);
    }

    @Cuando("rechazo el pedido {string} sin motivo")
    public void rejectWithoutReason(String name) throws Exception {
        session.send(post("/api/v1/orders/" + session.id(name) + "/reject"), Map.of());
    }

    @Entonces("el motivo de rechazo del pedido {string} es {string}")
    public void rejectionReasonIs(String name, String reason) throws Exception {
        assertThat(order(name).path("rejectionReason").asText()).isEqualTo(reason);
    }

    // ---------- US21 Consultar estado ----------

    @Cuando("consulto el pedido {string}")
    public void consultOrder(String name) throws Exception {
        session.send(get("/api/v1/orders/" + session.id(name)), null);
    }

    @Cuando("consulto los pedidos")
    public void listOrders() throws Exception {
        session.send(get("/api/v1/orders"), null);
    }

    @Entonces("el pedido muestra por material:")
    public void orderShowsPerMaterial(DataTable expected) throws Exception {
        session.expectStatus(200);
        JsonNode lines = session.body().path("lines");
        for (Map<String, String> row : expected.asMaps()) {
            JsonNode line = null;
            for (JsonNode candidate : lines) {
                if (candidate.path("sku").asText().equalsIgnoreCase(row.get("material"))) {
                    line = candidate;
                }
            }
            assertThat(line).as("línea de %s", row.get("material")).isNotNull();
            assertThat(line.path("requested").decimalValue()).isEqualByComparingTo(row.get("solicitado"));
            assertThat(line.path("dispatched").decimalValue()).isEqualByComparingTo(row.get("despachado"));
            assertThat(line.path("received").decimalValue()).isEqualByComparingTo(row.get("recibido"));
            assertThat(line.path("pending").decimalValue()).isEqualByComparingTo(row.get("pendiente"));
        }
        assertThat(lines.size()).isEqualTo(expected.asMaps().size());
    }

    @Entonces("la lista de pedidos contiene exactamente:")
    public void ordersAre(List<String> names) throws Exception {
        session.expectStatus(200);
        List<UUID> ids = new ArrayList<>();
        session.body().forEach(order -> ids.add(UUID.fromString(order.path("id").asText())));
        assertThat(ids).containsExactlyInAnyOrderElementsOf(names.stream().map(session::id).toList());
    }

    private JsonNode order(String name) throws Exception {
        ApiResponse response = session.perform(get("/api/v1/orders/" + session.id(name)), null, session.accessToken());
        assertThat(response.status()).as("consultar pedido %s", name).isEqualTo(200);
        return session.read(response);
    }
}
