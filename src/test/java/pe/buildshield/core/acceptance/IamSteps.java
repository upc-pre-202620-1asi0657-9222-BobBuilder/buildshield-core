package pe.buildshield.core.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import pe.buildshield.core.acceptance.CucumberSpringConfiguration.CapturingEmailPort;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.support.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.buildshield.core.acceptance.ApiRequest.get;
import static pe.buildshield.core.acceptance.ApiRequest.post;

/** Pasos de las historias US01 a US05 y pasos comunes de respuesta. */
public class IamSteps {

    @Autowired
    ScenarioSession session;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    CapturingEmailPort emails;

    @Autowired
    JwtDecoder jwtDecoder;

    // ---------- US01 Registro de organización ----------

    @Dado("que la organización {string} con RUC {string} está registrada con el administrador {string} y contraseña {string}")
    public void organizationIsRegistered(String legalName, String ruc, String email, String password) throws Exception {
        signUp(legalName, ruc, "Administrador " + legalName, email, password);
        session.expectStatus(201);
        session.put(email, UUID.fromString(session.body().path("administratorId").asText()));
    }

    @Cuando("registro la organización {string} con RUC {string}, administrador {string}, correo {string} y contraseña {string}")
    public void signUp(String legalName, String ruc, String fullName, String email, String password) throws Exception {
        session.send(post("/api/v1/auth/sign-up"), Map.of("ruc", ruc, "legalName", legalName, "adminFullName", fullName,
                "adminEmail", email, "password", password));
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
        session.expectStatus(status);
    }

    @Entonces("la operación es rechazada con estado {int}")
    public void rejectedWith(int status) throws Exception {
        session.expectStatus(status);
    }

    @Entonces("la operación es rechazada con estado {int} y código {string}")
    public void rejectedWithCode(int status, String code) throws Exception {
        session.expectStatus(status);
        assertThat(session.body().path("code").asText()).isEqualTo(code);
    }

    // ---------- US02 Inicio de sesión ----------

    @Cuando("inicio sesión con el correo {string} y la contraseña {string}")
    public void signIn(String email, String password) throws Exception {
        session.accessToken(null);
        session.send(post("/api/v1/auth/sign-in"), Map.of("email", email, "password", password));
        if (session.status() == 200) {
            session.accessToken(session.body().path("accessToken").asText());
            session.refreshToken(session.body().path("refreshToken").asText());
            session.previousRefreshToken(null);
            session.rememberSession(email, session.accessToken(), session.refreshToken());
        }
    }

    @Dado("que {string} inició sesión con la contraseña {string}")
    public void signedIn(String email, String password) throws Exception {
        signIn(email, password);
        session.expectStatus(200);
    }

    /** Verificación aparte: no reemplaza la sesión del escenario. */
    @Entonces("{string} puede iniciar sesión con la contraseña {string} como {string}")
    public void canSignInAs(String email, String password, String roleName) throws Exception {
        ApiResponse response = session.perform(post("/api/v1/auth/sign-in"),
                Map.of("email", email, "password", password), null);
        assertThat(response.status()).isEqualTo(200);
        Jwt jwt = jwtDecoder.decode(session.read(response).path("accessToken").asText());
        assertThat(jwt.getClaimAsString("role")).isEqualTo(role(roleName));
    }

    @Entonces("{string} no puede iniciar sesión con la contraseña {string}")
    public void cannotSignIn(String email, String password) throws Exception {
        ApiResponse response = session.perform(post("/api/v1/auth/sign-in"),
                Map.of("email", email, "password", password), null);
        assertThat(response.status()).isEqualTo(401);
    }

    @Entonces("recibo un token de acceso válido por {int} minutos y un token de renovación")
    public void receivedTokens(int minutes) throws Exception {
        JsonNode tokens = session.body();
        Jwt jwt = jwtDecoder.decode(tokens.path("accessToken").asText());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(minutes));
        assertThat(Instant.parse(tokens.path("accessTokenExpiresAt").asText())).isEqualTo(jwt.getExpiresAt());
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
    }

    @Cuando("renuevo la sesión")
    public void refresh() throws Exception {
        session.send(post("/api/v1/auth/refresh"), Map.of("refreshToken", session.refreshToken()));
        if (session.status() == 200) {
            session.previousRefreshToken(session.refreshToken());
            session.accessToken(session.body().path("accessToken").asText());
            session.refreshToken(session.body().path("refreshToken").asText());
        }
    }

    @Entonces("el token de renovación anterior ya no permite renovar la sesión")
    public void previousRefreshTokenIsUseless() throws Exception {
        String token = session.previousRefreshToken() != null ? session.previousRefreshToken() : session.refreshToken();
        ApiResponse response = session.perform(post("/api/v1/auth/refresh"), Map.of("refreshToken", token), null);
        assertThat(response.status()).isEqualTo(401);
        assertThat(session.read(response).path("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
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
        session.send(post("/api/v1/auth/sign-out"), Map.of("refreshToken", session.refreshToken()));
    }

    @Dado("que cerré la sesión")
    public void signedOut() throws Exception {
        signOut();
        session.expectStatus(204);
    }

    @Cuando("cierro la sesión sin token de acceso")
    public void signOutWithoutToken() throws Exception {
        session.lastResponse(session.perform(post("/api/v1/auth/sign-out"),
                Map.of("refreshToken", session.refreshToken()), null));
    }

    // ---------- US04 Gestión de usuarios ----------

    @Cuando("creo el usuario {string} con correo {string}, rol {string} y contraseña {string}")
    public void createUser(String fullName, String email, String roleName, String password) throws Exception {
        session.send(post("/api/v1/users"), Map.of("fullName", fullName, "email", email, "role", role(roleName),
                "password", password));
        if (session.status() == 201) {
            session.put(email, UUID.fromString(session.body().path("id").asText()));
        }
    }

    @Dado("que creé el usuario {string} con correo {string}, rol {string} y contraseña {string}")
    public void userCreated(String fullName, String email, String roleName, String password) throws Exception {
        createUser(fullName, email, roleName, password);
        session.expectStatus(201);
    }

    @Cuando("consulto la lista de usuarios")
    public void listUsers() throws Exception {
        session.send(get("/api/v1/users"), null);
    }

    @Entonces("la lista de usuarios contiene exactamente:")
    public void usersAre(List<String> expectedEmails) throws Exception {
        List<String> emailsInList = new ArrayList<>();
        session.body().forEach(user -> emailsInList.add(user.path("email").asText()));
        assertThat(emailsInList).containsExactlyInAnyOrderElementsOf(expectedEmails);
    }

    @Cuando("desactivo al usuario {string}")
    public void deactivate(String email) throws Exception {
        session.send(ApiRequest.patch("/api/v1/users/" + session.id(email)), Map.of("active", false));
    }

    @Dado("que desactivé al usuario {string}")
    public void deactivated(String email) throws Exception {
        deactivate(email);
        session.expectStatus(200);
    }

    @Cuando("reactivo al usuario {string}")
    public void reactivate(String email) throws Exception {
        session.send(ApiRequest.patch("/api/v1/users/" + session.id(email)), Map.of("active", true));
    }

    @Cuando("cambio el rol de {string} a {string}")
    public void changeRole(String email, String roleName) throws Exception {
        session.send(ApiRequest.patch("/api/v1/users/" + session.id(email)), Map.of("role", role(roleName)));
    }

    @Entonces("el usuario {string} figura inactivo")
    public void userIsInactive(String email) throws Exception {
        ApiResponse response = session.perform(get("/api/v1/users"), null, session.accessToken());
        boolean found = false;
        for (JsonNode user : session.read(response)) {
            if (user.path("email").asText().equals(email)) {
                found = true;
                assertThat(user.path("active").asBoolean()).isFalse();
            }
        }
        assertThat(found).as("usuario %s en la lista", email).isTrue();
    }

    @Entonces("la sesión que tenía {string} ya no sirve")
    public void previousSessionIsRevoked(String email) throws Exception {
        String[] tokens = session.sessionOf(email);
        ApiResponse access = session.perform(get("/api/v1/orders"), null, tokens[0]);
        assertThat(access.status()).as("token de acceso anterior").isEqualTo(401);
        assertThat(session.read(access).path("code").asText()).isEqualTo("TOKEN_REVOKED");
        ApiResponse refresh = session.perform(post("/api/v1/auth/refresh"), Map.of("refreshToken", tokens[1]), null);
        assertThat(refresh.status()).as("token de renovación anterior").isEqualTo(401);
    }

    // ---------- US05 Recuperación de contraseña ----------

    @Cuando("solicito recuperar la contraseña de {string}")
    public void requestReset(String email) throws Exception {
        session.send(post("/api/v1/auth/password-reset"), Map.of("email", email));
    }

    @Dado("que {string} solicitó recuperar su contraseña")
    public void resetRequested(String email) throws Exception {
        requestReset(email);
        session.expectStatus(202);
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
        session.send(post("/api/v1/auth/password-reset/confirm"), Map.of("token", token, "newPassword", newPassword));
    }

    @Dado("que confirmé la recuperación con el enlace recibido y la nueva contraseña {string}")
    public void resetConfirmed(String newPassword) throws Exception {
        confirmReset(newPassword);
        session.expectStatus(204);
    }

    private long countOrganizations(String ruc) {
        return jdbc.queryForObject("SELECT count(*) FROM organization.organizations WHERE ruc = ?", Long.class, ruc);
    }

    /** "encargado de almacén" → WAREHOUSE_MANAGER; un nombre desconocido se envía tal cual. */
    static String role(String displayName) {
        return Arrays.stream(Role.values())
                .filter(role -> role.displayName().equalsIgnoreCase(displayName))
                .map(Role::name)
                .findFirst()
                .orElse(displayName);
    }
}
