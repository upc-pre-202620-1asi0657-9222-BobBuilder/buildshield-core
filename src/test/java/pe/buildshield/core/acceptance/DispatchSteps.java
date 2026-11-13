package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.buildshield.core.acceptance.ApiRequest.get;
import static pe.buildshield.core.acceptance.ApiRequest.patch;
import static pe.buildshield.core.acceptance.ApiRequest.post;

/** Pasos de US22, US23, US24, US27 y US30 (módulo dispatch). */
public class DispatchSteps {

    @Autowired
    ScenarioSession session;

    // ---------- US22, US23 Generar despacho y partición del pedido ----------

    @Cuando("despacho del pedido {string} el despacho {string} con:")
    public void dispatch(String order, String name, DataTable lines) throws Exception {
        JsonNode orderLines = order(order).path("lines");
        List<Map<String, Object>> body = new ArrayList<>();
        for (Map<String, String> row : lines.asMaps()) {
            String lineId = null;
            for (JsonNode line : orderLines) {
                if (line.path("sku").asText().equalsIgnoreCase(row.get("material"))) {
                    lineId = line.path("id").asText();
                }
            }
            body.add(Map.of("orderLineId", lineId == null ? UUID.randomUUID().toString() : lineId,
                    "quantity", new BigDecimal(row.get("cantidad"))));
        }
        session.send(post("/api/v1/dispatches"), Map.of("orderId", session.id(order).toString(), "lines", body));
        if (session.status() == 201) {
            session.remember(name);
        }
    }

    @Dado("que despaché del pedido {string} el despacho {string} con:")
    public void dispatched(String order, String name, DataTable lines) throws Exception {
        dispatch(order, name, lines);
        session.expectStatus(201);
    }

    @Entonces("el despacho {string} está en estado {string}")
    public void dispatchIsIn(String name, String statusLabel) throws Exception {
        assertThat(dispatchJson(name).path("statusLabel").asText()).isEqualTo(statusLabel);
    }

    @Entonces("el despacho {string} es de tipo {string}")
    public void dispatchIsOfType(String name, String typeLabel) throws Exception {
        assertThat(dispatchJson(name).path("typeLabel").asText()).isEqualTo(typeLabel);
    }

    @Cuando("consulto los despachos del pedido {string}")
    public void listDispatchesOf(String order) throws Exception {
        session.send(get("/api/v1/dispatches").param("orderId", session.id(order).toString()), null);
    }

    @Cuando("consulto los despachos en tránsito")
    public void listInTransit() throws Exception {
        session.send(get("/api/v1/dispatches").param("status", "IN_TRANSIT"), null);
    }

    @Entonces("la lista de despachos contiene exactamente:")
    public void dispatchesAre(List<String> names) throws Exception {
        session.expectStatus(200);
        List<UUID> ids = new ArrayList<>();
        session.body().forEach(dispatch -> ids.add(UUID.fromString(dispatch.path("id").asText())));
        assertThat(ids).containsExactlyInAnyOrderElementsOf(names.stream().map(session::id).toList());
    }

    @Entonces("la lista de despachos está vacía")
    public void noDispatches() throws Exception {
        session.expectStatus(200);
        assertThat(session.body().size()).isZero();
    }

    // ---------- US30 Transportista ----------

    @Cuando("registro el transportista {string} con documento {string} y placa {string} en el despacho {string}")
    public void assignCarrier(String carrier, String document, String plate, String name) throws Exception {
        session.send(patch("/api/v1/dispatches/" + session.id(name) + "/carrier"),
                Map.of("carrierName", carrier, "carrierDocument", document, "plate", plate));
    }

    @Dado("que registré el transportista {string} con documento {string} y placa {string} en el despacho {string}")
    public void carrierAssigned(String carrier, String document, String plate, String name) throws Exception {
        assignCarrier(carrier, document, plate, name);
        session.expectStatus(200);
    }

    @Entonces("el transportista del despacho {string} es {string} con placa {string}")
    public void carrierIs(String name, String carrier, String plate) throws Exception {
        JsonNode found = dispatchJson(name).path("carrier");
        assertThat(found.path("name").asText()).isEqualTo(carrier);
        assertThat(found.path("plate").asText()).isEqualTo(plate);
    }

    // ---------- US27 Pesaje de salida ----------

    @Cuando("registro el pesaje de salida del despacho {string} con bruto {string} kg y tara {string} kg")
    public void weigh(String name, String gross, String tare) throws Exception {
        session.send(post("/api/v1/dispatches/" + session.id(name) + "/departure-weighing"), Map.of(
                "grossKg", new BigDecimal(gross), "tareKg", new BigDecimal(tare),
                "ticketPhotoUrl", "https://evidencias.buildshield.pe/tickets/" + name + ".jpg"));
    }

    @Dado("que registré el pesaje de salida del despacho {string} con bruto {string} kg y tara {string} kg")
    public void weighed(String name, String gross, String tare) throws Exception {
        weigh(name, gross, tare);
        session.expectStatus(200);
    }

    @Entonces("el pesaje de salida del despacho {string} tiene neto {string} kg")
    public void netIs(String name, String net) throws Exception {
        assertThat(dispatchJson(name).path("departureWeighing").path("netKg").decimalValue()).isEqualByComparingTo(net);
    }

    // ---------- Salida del almacén ----------

    @Cuando("hago salir el despacho {string}")
    public void depart(String name) throws Exception {
        session.send(post("/api/v1/dispatches/" + session.id(name) + "/depart"), null);
    }

    @Dado("que el despacho {string} salió con el transportista {string}")
    public void departedWith(String name, String carrier) throws Exception {
        carrierAssigned(carrier, "20555666777", "ABC-123", name);
        weighed(name, "2550.5", "1050.5");
        depart(name);
        session.expectStatus(200);
    }

    // ---------- US24 Manifiesto ----------

    @Cuando("consulto el manifiesto del despacho {string}")
    public void manifest(String name) throws Exception {
        session.send(get("/api/v1/dispatches/" + session.id(name) + "/manifest"), null);
    }

    @Entonces("el manifiesto tiene el código del despacho {string} y su QR lo codifica")
    public void manifestCodeAndQr(String name) throws Exception {
        session.expectStatus(200);
        JsonNode manifest = session.body();
        String code = dispatchJson(name).path("manifestCode").asText();
        assertThat(manifest.path("manifestCode").asText()).isEqualTo(code).startsWith("MAN-");
        byte[] png = Base64.getDecoder().decode(manifest.path("qrCodePngBase64").asText());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image)))).getText();
        assertThat(decoded).isEqualTo(code);
    }

    @Entonces("el manifiesto muestra la obra {string}, el almacén {string} y los materiales:")
    public void manifestShows(String worksite, String warehouse, DataTable materials) throws Exception {
        JsonNode manifest = session.body();
        assertThat(manifest.path("worksite").path("name").asText()).isEqualTo(worksite);
        assertThat(manifest.path("warehouse").path("name").asText()).isEqualTo(warehouse);
        List<Map<String, String>> expected = materials.asMaps();
        assertThat(manifest.path("lines").size()).isEqualTo(expected.size());
        for (Map<String, String> row : expected) {
            JsonNode line = null;
            for (JsonNode candidate : manifest.path("lines")) {
                if (candidate.path("sku").asText().equals(row.get("sku"))) {
                    line = candidate;
                }
            }
            assertThat(line).as("material %s en el manifiesto", row.get("sku")).isNotNull();
            assertThat(line.path("name").asText()).isEqualTo(row.get("nombre"));
            assertThat(line.path("unit").asText()).isEqualTo(row.get("unidad"));
            assertThat(line.path("quantity").decimalValue()).isEqualByComparingTo(row.get("cantidad"));
        }
    }

    @Entonces("el manifiesto muestra el transportista {string} y el neto {string} kg")
    public void manifestShowsCarrierAndWeighing(String carrier, String net) throws Exception {
        JsonNode manifest = session.body();
        assertThat(manifest.path("carrier").path("name").asText()).isEqualTo(carrier);
        assertThat(manifest.path("departureWeighing").path("netKg").decimalValue()).isEqualByComparingTo(net);
    }

    private JsonNode dispatchJson(String name) throws Exception {
        ApiResponse response = session.perform(get("/api/v1/dispatches/" + session.id(name)), null, session.accessToken());
        assertThat(response.status()).as("consultar despacho %s: %s", name, response.body()).isEqualTo(200);
        return session.read(response);
    }

    private JsonNode order(String name) throws Exception {
        ApiResponse response = session.perform(get("/api/v1/orders/" + session.id(name)), null, session.accessToken());
        if (response.status() != 200) {
            return session.read(new ApiResponse(200, "{\"lines\":[]}"));
        }
        return session.read(response);
    }
}
