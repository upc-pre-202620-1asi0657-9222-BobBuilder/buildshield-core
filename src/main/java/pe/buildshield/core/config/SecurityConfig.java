package pe.buildshield.core.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.commons.error.ErrorResponseWriter;
import pe.buildshield.commons.security.JwtAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Seguridad del Core: API sin sesión de servidor, autenticada con el JWT de commons.
 *
 * <p>Dos cadenas:
 * <ol>
 *   <li>Endpoints públicos (registro, inicio de sesión, renovación, recuperación, documentación y
 *       salud): <b>sin</b> filtro JWT. Un token vencido o revocado que el cliente envíe por costumbre
 *       no debe impedir renovar la sesión ni iniciarla de nuevo.</li>
 *   <li>Todo lo demás: exige un JWT válido. La autorización por rol está en cada endpoint con
 *       {@code @PreAuthorize}.</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

    /** Endpoints que no requieren token: registro, inicio de sesión, renovación y recuperación. */
    static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/sign-up",
            "/api/v1/auth/sign-in",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password-reset",
            "/api/v1/auth/password-reset/confirm"
    };

    static final String[] API_DOCS = {"/api/v1/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    @Order(1)
    SecurityFilterChain publicEndpoints(HttpSecurity http) throws Exception {
        return stateless(http)
                .securityMatcher(publicRequests())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain authenticatedApi(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
            ErrorResponseWriter errorWriter) throws Exception {
        return stateless(http)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> errorWriter.write(response,
                                HttpStatus.UNAUTHORIZED.value(),
                                ErrorResponse.of("UNAUTHENTICATED", "Debes iniciar sesión para usar este recurso")))
                        .accessDeniedHandler((request, response, ex) -> errorWriter.write(response,
                                HttpStatus.FORBIDDEN.value(),
                                ErrorResponse.of("FORBIDDEN", "No tienes permiso para realizar esta operación"))))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    static RequestMatcher publicRequests() {
        List<RequestMatcher> matchers = new ArrayList<>();
        matchers.add(EndpointRequest.to(HealthEndpoint.class));
        for (String path : PUBLIC_AUTH_ENDPOINTS) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path));
        }
        for (String path : API_DOCS) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(path));
        }
        return new OrRequestMatcher(matchers);
    }
}
