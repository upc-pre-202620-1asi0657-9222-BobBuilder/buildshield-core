package pe.buildshield.core.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.buildshield.core.iam.application.AuthenticationService;

import java.time.Instant;
import java.util.UUID;

/** Recursos (DTO) de la API de autenticación. */
final class AuthResources {

    private AuthResources() {
    }

    @Schema(description = "Datos para registrar una organización y su administrador")
    record SignUpRequest(
            @Schema(description = "RUC de 11 dígitos", example = "20123456789")
            @NotBlank String ruc,
            @Schema(description = "Razón social", example = "Constructora Andina SAC")
            @NotBlank @Size(max = 200) String legalName,
            @Schema(description = "Nombre del administrador", example = "Ana Torres")
            @NotBlank @Size(max = 150) String adminFullName,
            @Schema(description = "Correo del administrador; con él inicia sesión", example = "ana@andina.pe")
            @NotBlank @Size(max = 254) String adminEmail,
            @Schema(description = "8 a 72 caracteres, con al menos una letra y un número", example = "Segura123")
            @NotBlank String password) {
    }

    @Schema(description = "Organización y administrador creados")
    record SignUpResponse(UUID organizationId, UUID administratorId) {
    }

    @Schema(description = "Credenciales de inicio de sesión")
    record SignInRequest(
            @Schema(example = "ana@andina.pe") @NotBlank String email,
            @Schema(example = "Segura123") @NotBlank String password) {
    }

    @Schema(description = "Token de renovación entregado al iniciar sesión o en la última renovación")
    record RefreshRequest(@NotBlank String refreshToken) {
    }

    @Schema(description = "Token de renovación de la sesión que se cierra (opcional)")
    record SignOutRequest(String refreshToken) {
    }

    @Schema(description = "Correo de la cuenta a recuperar")
    record PasswordResetRequest(@Schema(example = "ana@andina.pe") @NotBlank String email) {
    }

    @Schema(description = "Token del enlace recibido por correo y nueva contraseña")
    record PasswordResetConfirmRequest(
            @NotBlank String token,
            @Schema(description = "8 a 72 caracteres, con al menos una letra y un número", example = "Nueva12345")
            @NotBlank String newPassword) {
    }

    @Schema(description = "Tokens de la sesión")
    record TokenResponse(
            @Schema(description = "JWT RS256 para la cabecera Authorization: Bearer; vence en 15 minutos")
            String accessToken,
            Instant accessTokenExpiresAt,
            @Schema(description = "Token opaco para renovar la sesión; cada uso lo reemplaza por uno nuevo")
            String refreshToken,
            Instant refreshTokenExpiresAt,
            @Schema(example = "Bearer") String tokenType) {

        static TokenResponse from(AuthenticationService.SessionTokens tokens) {
            return new TokenResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(), tokens.refreshToken(),
                    tokens.refreshTokenExpiresAt(), "Bearer");
        }
    }
}
