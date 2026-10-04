package pe.buildshield.core.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.commons.error.ErrorResponseWriter;
import pe.buildshield.commons.security.JwtAuthenticationFilter;

/**
 * Seguridad del Core: API sin sesión de servidor, autenticada con el JWT de commons. Por URL solo se
 * separa lo público de lo autenticado; la autorización por rol está en cada endpoint con
 * {@code @PreAuthorize}.
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
            ErrorResponseWriter errorWriter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        .anyRequest().authenticated())
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
}
