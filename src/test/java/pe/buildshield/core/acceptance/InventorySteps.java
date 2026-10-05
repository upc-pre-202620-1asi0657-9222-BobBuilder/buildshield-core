package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de carga y consulta de existencias (módulo inventory). */
public class InventorySteps {

    @Autowired
    ScenarioSession session;

    @Cuando("registro una entrada de {string} del material {string} en el almacén {string}")
    public void registerEntry(String quantity, String sku, String warehouse) throws Exception {
        session.send(post("/api/v1/stock/entries"), Map.of(
                "warehouseId", session.id(warehouse).toString(),
                "materialId", session.id(sku.toUpperCase()).toString(),
                "quantity", new BigDecimal(quantity)));
    }

    @Dado("que el almacén {string} tiene {string} del material {string}")
    public void warehouseHasStock(String warehouse, String quantity, String sku) throws Exception {
        registerEntry(quantity, sku, warehouse);
        session.expectStatus(201);
    }

    @Entonces("el stock disponible del material {string} en el almacén {string} es {string}")
    public void availableStockIs(String sku, String warehouse, String expected) throws Exception {
        MvcResult response = session.perform(get("/api/v1/stock").param("warehouseId", session.id(warehouse).toString()),
                null, session.accessToken());
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode found = null;
        for (JsonNode item : session.read(response)) {
            if (item.path("sku").asText().equalsIgnoreCase(sku)) {
                found = item;
            }
        }
        assertThat(found).as("stock de %s en %s", sku, warehouse).isNotNull();
        assertThat(found.path("availableQty").decimalValue()).isEqualByComparingTo(expected);
    }

    @Cuando("consulto el stock")
    public void listStock() throws Exception {
        session.send(get("/api/v1/stock"), null);
    }

    @Entonces("el stock listado es solo del almacén {string}")
    public void stockOnlyFrom(String warehouse) throws Exception {
        session.expectStatus(200);
        JsonNode items = session.body();
        assertThat(items.size()).as("hay stock listado").isPositive();
        for (JsonNode item : items) {
            assertThat(item.path("warehouseId").asText()).isEqualTo(session.id(warehouse).toString());
        }
    }
}
