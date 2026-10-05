package pe.buildshield.core.iam.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.buildshield.core.shared.config.BuildshieldProperties;
import pe.buildshield.core.shared.security.JwtTokenIssuer;
import pe.buildshield.core.shared.security.PemKeys;
import pe.buildshield.core.shared.security.RevokedTokenStore;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.iam.application.AuthenticationService;
import pe.buildshield.core.iam.application.PasswordResetService;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.support.WebSliceTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El filtro JWT del kernel compartido dentro de la cadena de seguridad del Core, sobre un endpoint que exige
 * autenticación (cierre de sesión).
 */
@WebMvcTest(AuthController.class)
@WebSliceTest
class JwtAuthenticationFilterTest {

    private static final TenantInfo ANA = new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "ADMINISTRATOR");

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenIssuer issuer;

    @Autowired
    BuildshieldProperties properties;

    @MockitoBean
    RevokedTokenStore revokedTokens;

    @MockitoBean
    AuthenticationService authenticationService;

    @MockitoBean
    SignUpService signUpService;

    @MockitoBean
    PasswordResetService passwordResetService;

    @Test
    void request_without_token_is_401() throws Exception {
        mvc.perform(post("/api/v1/auth/sign-out"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expired_token_is_401() throws Exception {
        JwtTokenIssuer pastIssuer = new JwtTokenIssuer(
                PemKeys.publicKey(properties.getSecurity().getJwtPublicKey()),
                PemKeys.privateKey(properties.getSecurity().getJwtPrivateKey()),
                properties.getSecurity().getIssuer(),
                Clock.fixed(Instant.now().minus(Duration.ofMinutes(20)), ZoneOffset.UTC));

        mvc.perform(post("/api/v1/auth/sign-out").header("Authorization", "Bearer " + pastIssuer.issue(ANA).value()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void revoked_token_is_401() throws Exception {
        JwtTokenIssuer.IssuedToken token = issuer.issue(ANA);
        when(revokedTokens.isRevoked(token.tokenId())).thenReturn(true);

        mvc.perform(post("/api/v1/auth/sign-out").header("Authorization", "Bearer " + token.value()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void malformed_token_is_401() throws Exception {
        mvc.perform(post("/api/v1/auth/sign-out").header("Authorization", "Bearer no.es.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void valid_token_reaches_the_endpoint_with_the_user_and_token_id() throws Exception {
        JwtTokenIssuer.IssuedToken token = issuer.issue(ANA);

        mvc.perform(post("/api/v1/auth/sign-out").header("Authorization", "Bearer " + token.value()))
                .andExpect(status().isNoContent());

        verify(authenticationService).signOut(ANA.userId(), token.tokenId(), null);
    }

    @Test
    void an_expired_or_revoked_token_does_not_block_refreshing_the_session() throws Exception {
        JwtTokenIssuer.IssuedToken revoked = issuer.issue(ANA);
        when(revokedTokens.isRevoked(revoked.tokenId())).thenReturn(true);
        when(authenticationService.refresh("r")).thenReturn(new AuthenticationService.SessionTokens(
                "a", Instant.now(), "r2", Instant.now()));

        mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + revoked.value())
                        .contentType("application/json").content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer basura")
                        .contentType("application/json").content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void public_endpoints_do_not_need_a_token() throws Exception {
        when(authenticationService.refresh(any())).thenThrow(
                pe.buildshield.core.iam.domain.model.AuthenticationFailedException.invalidRefreshToken());

        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
