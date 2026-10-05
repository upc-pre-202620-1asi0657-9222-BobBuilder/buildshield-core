package pe.buildshield.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pe.buildshield.core.support.CoreIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La especificación OpenAPI es pública y documenta los endpoints de iam con su esquema de seguridad. */
@CoreIntegrationTest
@AutoConfigureMockMvc
class OpenApiDocumentationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void api_docs_are_public_and_describe_the_iam_endpoints() throws Exception {
        String body = mvc.perform(get("/api/v1/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode spec = json.readTree(body);

        assertThat(spec.path("paths").fieldNames()).toIterable().contains(
                "/api/v1/auth/sign-up", "/api/v1/auth/sign-in", "/api/v1/auth/refresh", "/api/v1/auth/sign-out",
                "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm", "/api/v1/users");
        assertThat(spec.at("/paths/~1api~1v1~1users/post/responses").fieldNames()).toIterable()
                .contains("201", "400", "403", "409");
        assertThat(spec.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/paths/~1api~1v1~1auth~1sign-up/post/summary").asText())
                .isEqualTo("Registrar una organización y su administrador");
    }

    @Test
    void api_docs_describe_the_organization_endpoints() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/api/v1/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        for (String resource : new String[] {"worksites", "warehouses", "materials", "assignments"}) {
            JsonNode collection = spec.at("/paths/~1api~1v1~1" + resource);
            JsonNode item = spec.at("/paths/~1api~1v1~1" + resource + "~1{id}");
            assertThat(collection.has("post")).as("POST /%s", resource).isTrue();
            assertThat(collection.has("get")).as("GET /%s", resource).isTrue();
            assertThat(item.has("get")).as("GET /%s/{id}", resource).isTrue();
            assertThat(item.has("patch")).as("PATCH /%s/{id}", resource).isTrue();
            assertThat(item.at("/get/responses").has("404")).as("404 documentado en /%s/{id}", resource).isTrue();
        }
        assertThat(spec.at("/paths/~1api~1v1~1materials/post/responses").has("409")).isTrue();
    }

    @Test
    void api_docs_describe_orders_and_stock() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/api/v1/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(spec.at("/paths/~1api~1v1~1orders").has("post")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1orders").has("get")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1orders~1{id}").has("get")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1orders~1{id}~1approve/post/responses").has("409")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1orders~1{id}~1reject/post/responses").has("400")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1stock~1entries/post/responses").has("201")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1stock").has("get")).isTrue();
    }

    @Test
    void swagger_ui_is_public() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
