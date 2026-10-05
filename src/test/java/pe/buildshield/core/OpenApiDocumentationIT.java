package pe.buildshield.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.core.support.CoreIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La especificación OpenAPI es pública y documenta los endpoints de iam con su esquema de seguridad. */
@CoreIntegrationTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
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
    void swagger_ui_is_public() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
