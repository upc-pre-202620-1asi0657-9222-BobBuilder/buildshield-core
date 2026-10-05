package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estado de un escenario compartido entre las clases de pasos: sesión del usuario actual, última
 * respuesta y los identificadores de lo creado, por nombre. Tiene scope de escenario.
 */
public class ScenarioSession {

    private final MockMvc mvc;
    private final ObjectMapper json;

    private String accessToken;
    private String refreshToken;
    private String previousRefreshToken;
    private MvcResult lastResponse;

    /** Identificadores por nombre visible: obras, almacenes, materiales (por SKU) y usuarios (por correo). */
    private final Map<String, UUID> ids = new HashMap<>();

    public ScenarioSession(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Envía la petición con el token de la sesión actual (si hay) y la guarda como última respuesta. */
    public MvcResult send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        lastResponse = perform(request, body, accessToken);
        return lastResponse;
    }

    /** Envía sin usar ni modificar la sesión ni la última respuesta. */
    public MvcResult perform(MockHttpServletRequestBuilder request, Object body, String bearer) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request).andReturn();
    }

    public int status() {
        return lastResponse.getResponse().getStatus();
    }

    public JsonNode body() throws Exception {
        return read(lastResponse);
    }

    public JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    public void expectStatus(int expected) throws Exception {
        assertThat(status()).as("respuesta: %s", lastResponse.getResponse().getContentAsString()).isEqualTo(expected);
    }

    public UUID id(String name) {
        UUID id = ids.get(name);
        assertThat(id).as("no se registró '%s' en el escenario", name).isNotNull();
        return id;
    }

    public void remember(String name) throws Exception {
        ids.put(name, UUID.fromString(body().path("id").asText()));
    }

    // Acceso por métodos: el bean es un proxy de scope de escenario y sus campos no se ven desde fuera.

    public String accessToken() {
        return accessToken;
    }

    public void accessToken(String value) {
        accessToken = value;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public void refreshToken(String value) {
        refreshToken = value;
    }

    public String previousRefreshToken() {
        return previousRefreshToken;
    }

    public void previousRefreshToken(String value) {
        previousRefreshToken = value;
    }

    public void lastResponse(MvcResult value) {
        lastResponse = value;
    }

    public void put(String name, UUID id) {
        ids.put(name, id);
    }
}
