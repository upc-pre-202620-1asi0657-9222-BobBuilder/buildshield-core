package pe.buildshield.core;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import pe.buildshield.core.support.ContainersConfig;
import static org.assertj.core.api.Assertions.assertThat;

/** Contrato por HTTP real: administrador autenticado consulta su historial. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ContainersConfig.class)
class AuditEndpointIT {
    @Autowired TestRestTemplate http;
    @Test void administrator_can_read_audit_history() {
        String email = UUID.randomUUID() + "@audit.test";
        String ruc = "20" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
        var registered = http.postForEntity("/api/v1/auth/sign-up", Map.of("ruc", ruc, "legalName", "Constructora",
                "adminFullName", "Administrador", "adminEmail", email, "password", "Segura123"), String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var session = http.postForEntity("/api/v1/auth/sign-in", Map.of("email", email, "password", "Segura123"), com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.getBody().path("accessToken").asText());
        var history = http.exchange("/api/v1/audit/events", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
