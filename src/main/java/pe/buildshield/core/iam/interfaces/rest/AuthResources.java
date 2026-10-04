package pe.buildshield.core.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
}
