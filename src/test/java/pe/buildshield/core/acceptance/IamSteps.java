package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.buildshield.core.acceptance.CucumberSpringConfiguration.CapturingEmailPort;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.support.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de las historias US01 a US05. Cada escenario tiene su propia instancia (estado del escenario). */
public class IamSteps {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    CapturingEmailPort emails;

    @Autowired
    JwtDecoder jwtDecoder;

    private MvcResult lastResponse;
    private String accessToken;
    private String refreshToken;
    private String previousRefreshToken;

    @Before
    public void cleanState() {
        jdbc.update("DELETE FROM iam.revoked_access_tokens");
        jdbc.update("DELETE FROM iam.password_reset_tokens");
        jdbc.update("DELETE FROM iam.refresh_tokens");
        jdbc.update("DELETE FROM iam.users");
        jdbc.update("DELETE FROM organization.organizations");
        emails.clear();
        clock.setTo(Instant.now());
    }

    // ---------- US01 Registro de organización ----------

    @Dado("que la organización {string} con RUC {string} está registrada con el administrador {string} y contraseña {string}")
    public void organizationIsRegistered(String legalName, String ruc, String email, String password) throws Exception {
        signUp(legalName, ruc, "Administrador " + legalName, email, password);
        assertStatus(201);
    }

    @Cuando("registro la organización {string} con RUC {string}, administrador {string}, correo {string} y contraseña {string}")
    public void signUp(String legalName, String ruc, String fullName, String email, String password) throws Exception {
        send(post("/api/v1/auth/sign-up"), Map.of("ruc", ruc, "legalName", legalName, "adminFullName", fullName,
                "adminEmail", email, "password", password), null);
    }

    @Entonces("existe la organización con RUC {string}")
    public void organizationExists(String ruc) {
        assertThat(countOrganizations(ruc)).isEqualTo(1);
    }

    @Entonces("no existe ninguna organización con RUC {string}")
    public void organizationDoesNotExist(String ruc) {
        assertThat(countOrganizations(ruc)).isZero();
    }

    // ---------- Respuestas ----------

    @Entonces("la respuesta tiene estado {int}")
    public void assertStatus(int status) throws Exception {
        assertThat(lastResponse.getResponse().getStatus())
                .as("respuesta: %s", lastResponse.getResponse().getContentAsString())
                .isEqualTo(status);
    }

    @Entonces("la operación es rechazada con estado {int}")
    public void rejectedWith(int status) throws Exception {
        assertStatus(status);
    }

    @Entonces("la operación es rechazada con estado {int} y código {string}")
    public void rejectedWithCode(int status, String code) throws Exception {
        assertStatus(status);
        assertThat(body().path("code").asText()).isEqualTo(code);
    }

    // ---------- US02 Inicio de sesión ----------

    @Cuando("inicio sesión con el correo {string} y la contraseña {string}")
    public void signIn(String email, String password) throws Exception {
        send(post("/api/v1/auth/sign-in"), Map.of("email", email, "password", password), null);
        if (lastResponse.getResponse().getStatus() == 200) {
            accessToken = body().path("accessToken").asText();
            refreshToken = body().path("refreshToken").asText();
            previousRefreshToken = null;
        }
    }

    @Dado("que {string} inició sesión con la contraseña {string}")
    public void signedIn(String email, String password) throws Exception {
        signIn(email, password);
        assertStatus(200);
    }

    /** Verificación aparte: no reemplaza la sesión del escenario. */
    @Entonces("{string} puede iniciar sesión con la contraseña {string} como {string}")
    public void canSignInAs(String email, String password, String roleName) throws Exception {
        MvcResult response = mvc.perform(json(post("/api/v1/auth/sign-in"),
                Map.of("email", email, "password", password), null)).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        String token = json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getClaimAsString("role")).isEqualTo(role(roleName));
    }

    @Entonces("{string} no puede iniciar sesión con la contraseña {string}")
    public void cannotSignIn(String email, String password) throws Exception {
        MvcResult response = mvc.perform(json(post("/api/v1/auth/sign-in"),
                Map.of("email", email, "password", password), null)).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(401);
    }

    @Entonces("recibo un token de acceso válido por {int} minutos y un token de renovación")
    public void receivedTokens(int minutes) throws Exception {
        JsonNode tokens = body();
        Jwt jwt = jwtDecoder.decode(tokens.path("accessToken").asText());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(minutes));
        assertThat(Instant.parse(tokens.path("accessTokenExpiresAt").asText())).isEqualTo(jwt.getExpiresAt());
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
    }

    @Cuando("renuevo la sesión")
    public void refresh() throws Exception {
        send(post("/api/v1/auth/refresh"), Map.of("refreshToken", refreshToken), null);
        if (lastResponse.getResponse().getStatus() == 200) {
            previousRefreshToken = refreshToken;
            accessToken = body().path("accessToken").asText();
            refreshToken = body().path("refreshToken").asText();
        }
    }

    @Entonces("el token de renovación anterior ya no permite renovar la sesión")
    public void previousRefreshTokenIsUseless() throws Exception {
        String token = previousRefreshToken != null ? previousRefreshToken : refreshToken;
        MvcResult response = mvc.perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", token), null))
                .andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(response.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Dado("pasaron {int} días")
    public void daysPassed(int days) {
        clock.advance(Duration.ofDays(days));
    }

    @Dado("pasaron {int} minutos")
    public void minutesPassed(int minutes) {
        clock.advance(Duration.ofMinutes(minutes));
    }

    // ---------- US03 Cierre de sesión ----------

    @Cuando("cierro la sesión")
    public void signOut() throws Exception {
        send(post("/api/v1/auth/sign-out"), Map.of("refreshToken", refreshToken), accessToken);
    }

    @Dado("que cerré la sesión")
    public void signedOut() throws Exception {
        signOut();
        assertStatus(204);
    }

    @Cuando("cierro la sesión sin token de acceso")
    public void signOutWithoutToken() throws Exception {
        send(post("/api/v1/auth/sign-out"), Map.of("refreshToken", refreshToken), null);
    }

    // ---------- US04 Gestión de usuarios ----------

    @Cuando("creo el usuario {string} con correo {string}, rol {string} y contraseña {string}")
    public void createUser(String fullName, String email, String roleName, String password) throws Exception {
        send(post("/api/v1/users"), Map.of("fullName", fullName, "email", email, "role", role(roleName),
                "password", password), accessToken);
    }

    @Dado("que creé el usuario {string} con correo {string}, rol {string} y contraseña {string}")
    public void userCreated(String fullName, String email, String roleName, String password) throws Exception {
        createUser(fullName, email, roleName, password);
        assertStatus(201);
    }

    @Cuando("consulto la lista de usuarios")
    public void listUsers() throws Exception {
        lastResponse = mvc.perform(authorized(get("/api/v1/users"), accessToken)).andReturn();
    }

    @Entonces("la lista de usuarios contiene exactamente:")
    public void usersAre(List<String> expectedEmails) throws Exception {
        List<String> emailsInList = new ArrayList<>();
        body().forEach(user -> emailsInList.add(user.path("email").asText()));
        assertThat(emailsInList).containsExactlyInAnyOrderElementsOf(expectedEmails);
    }

    // ---------- US05 Recuperación de contraseña ----------

    @Cuando("solicito recuperar la contraseña de {string}")
    public void requestReset(String email) throws Exception {
        send(post("/api/v1/auth/password-reset"), Map.of("email", email), null);
    }

    @Dado("que {string} solicitó recuperar su contraseña")
    public void resetRequested(String email) throws Exception {
        requestReset(email);
        assertStatus(202);
        sentResetEmailTo(email);
    }

    @Entonces("se envió un correo de recuperación a {string}")
    public void sentResetEmailTo(String email) {
        assertThat(emails.sent()).extracting(CucumberSpringConfiguration.SentEmail::to).contains(email);
    }

    @Entonces("no se envió ningún correo de recuperación")
    public void noEmailSent() {
        assertThat(emails.sent()).isEmpty();
    }

    @Cuando("confirmo la recuperación con el enlace recibido y la nueva contraseña {string}")
    public void confirmReset(String newPassword) throws Exception {
        String token = emails.sent().get(emails.sent().size() - 1).token();
        send(post("/api/v1/auth/password-reset/confirm"), Map.of("token", token, "newPassword", newPassword), null);
    }

    @Dado("que confirmé la recuperación con el enlace recibido y la nueva contraseña {string}")
    public void resetConfirmed(String newPassword) throws Exception {
        confirmReset(newPassword);
        assertStatus(204);
    }

    // ---------- Utilidades ----------

    private void send(MockHttpServletRequestBuilder request, Map<String, String> body, String bearer) throws Exception {
        lastResponse = mvc.perform(json(request, body, bearer)).andReturn();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, String> body,
            String bearer) throws Exception {
        return authorized(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), bearer);
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String bearer) {
        return bearer == null ? request : request.header("Authorization", "Bearer " + bearer);
    }

    private JsonNode body() throws Exception {
        return json.readTree(lastResponse.getResponse().getContentAsString());
    }

    private long countOrganizations(String ruc) {
        return jdbc.queryForObject("SELECT count(*) FROM organization.organizations WHERE ruc = ?", Long.class, ruc);
    }

    /** "encargado de almacén" → WAREHOUSE_MANAGER; un nombre desconocido se envía tal cual. */
    private static String role(String displayName) {
        return Arrays.stream(Role.values())
                .filter(role -> role.displayName().equalsIgnoreCase(displayName))
                .map(Role::name)
                .findFirst()
                .orElse(displayName);
    }
}
