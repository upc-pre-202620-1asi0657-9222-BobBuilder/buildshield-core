package pe.buildshield.core.iam.interfaces.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.buildshield.commons.security.JwtTokenIssuer;
import pe.buildshield.commons.security.RevokedTokenStore;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.iam.application.AuthenticationService;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.iam.application.UserManagementService;
import pe.buildshield.core.iam.domain.model.Role;
import pe.buildshield.core.support.WebSliceTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de autorización: cada endpoint del módulo iam contra usuario anónimo y cada rol. Además
 * verifica que ningún endpoint quede sin una regla explícita de {@code @PreAuthorize}.
 */
@WebMvcTest({AuthController.class, UsersController.class})
@WebSliceTest
class RoleAuthorizationTest {

    private static final Map<String, String> BODIES = Map.of(
            "sign-up", "{\"ruc\":\"20123456789\",\"legalName\":\"Andina\",\"adminFullName\":\"Ana\",\"adminEmail\":\"ana@andina.pe\",\"password\":\"Segura123\"}",
            "sign-in", "{\"email\":\"ana@andina.pe\",\"password\":\"Segura123\"}",
            "refresh", "{\"refreshToken\":\"abc\"}",
            "sign-out", "{\"refreshToken\":\"abc\"}",
            "create-user", "{\"fullName\":\"Rosa\",\"email\":\"rosa@andina.pe\",\"role\":\"WAREHOUSE_MANAGER\",\"password\":\"Almacen123\"}");

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenIssuer issuer;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    RevokedTokenStore revokedTokens;

    @MockitoBean
    SignUpService signUpService;

    @MockitoBean
    AuthenticationService authenticationService;

    @MockitoBean
    UserManagementService userManagementService;

    @BeforeEach
    void stubServices() {
        when(signUpService.signUp(any())).thenReturn(new SignUpService.SignUpResult(UUID.randomUUID(), UUID.randomUUID()));
        AuthenticationService.SessionTokens tokens = new AuthenticationService.SessionTokens("a", Instant.now(), "r", Instant.now());
        when(authenticationService.signIn(anyString(), anyString())).thenReturn(tokens);
        when(authenticationService.refresh(anyString())).thenReturn(tokens);
        when(userManagementService.create(any())).thenReturn(new UserManagementService.UserView(
                UUID.randomUUID(), "rosa@andina.pe", "Rosa", Role.WAREHOUSE_MANAGER, true));
        when(userManagementService.list()).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0} {1} como {2} -> {3}")
    @CsvSource({
            // endpoint              , cuerpo     , quién             , estado
            "POST, /api/v1/auth/sign-up , sign-up    , ANONYMOUS        , 201",
            "POST, /api/v1/auth/sign-in , sign-in    , ANONYMOUS        , 200",
            "POST, /api/v1/auth/refresh , refresh    , ANONYMOUS        , 200",
            "POST, /api/v1/auth/sign-out, sign-out   , ANONYMOUS        , 401",
            "POST, /api/v1/auth/sign-out, sign-out   , ADMINISTRATOR    , 204",
            "POST, /api/v1/auth/sign-out, sign-out   , WAREHOUSE_MANAGER, 204",
            "POST, /api/v1/auth/sign-out, sign-out   , SITE_MANAGER     , 204",
            "POST, /api/v1/users        , create-user, ANONYMOUS        , 401",
            "POST, /api/v1/users        , create-user, ADMINISTRATOR    , 201",
            "POST, /api/v1/users        , create-user, WAREHOUSE_MANAGER, 403",
            "POST, /api/v1/users        , create-user, SITE_MANAGER     , 403",
            "GET , /api/v1/users        ,            , ANONYMOUS        , 401",
            "GET , /api/v1/users        ,            , ADMINISTRATOR    , 200",
            "GET , /api/v1/users        ,            , WAREHOUSE_MANAGER, 403",
            "GET , /api/v1/users        ,            , SITE_MANAGER     , 403",
    })
    void endpoint_is_allowed_only_for_its_roles(String method, String path, String body, String who, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder request = "GET".equals(method) ? get(path) : post(path);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(BODIES.get(body));
        }
        if (!"ANONYMOUS".equals(who)) {
            String token = issuer.issue(new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), who)).value();
            request.header("Authorization", "Bearer " + token);
        }

        mvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    void every_iam_endpoint_declares_its_authorization_rule() {
        List<String> withoutRule = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().startsWith("pe.buildshield.core"))
                .filter(handler -> !hasPreAuthorize(handler))
                .map(handler -> handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName())
                .toList();

        assertThat(handlerMapping.getHandlerMethods()).isNotEmpty();
        assertThat(withoutRule).as("endpoints sin @PreAuthorize").isEmpty();
    }

    private static boolean hasPreAuthorize(HandlerMethod handler) {
        return handler.hasMethodAnnotation(PreAuthorize.class)
                || handler.getBeanType().isAnnotationPresent(PreAuthorize.class);
    }
}
