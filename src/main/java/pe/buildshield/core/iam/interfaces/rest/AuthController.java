package pe.buildshield.core.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.SignUpRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.SignUpResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro de organizaciones, sesiones y recuperación de contraseña")
class AuthController {

    private final SignUpService signUpService;

    AuthController(SignUpService signUpService) {
        this.signUpService = signUpService;
    }

    @PostMapping("/sign-up")
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una organización y su administrador",
            description = "Crea la organización y su usuario administrador en una sola transacción.")
    @ApiResponse(responseCode = "201", description = "Organización y administrador creados")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (RUC, correo o contraseña)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "RUC o correo ya registrados",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    SignUpResponse signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpService.SignUpResult result = signUpService.signUp(new SignUpService.SignUpCommand(
                request.ruc(), request.legalName(), request.adminFullName(), request.adminEmail(), request.password()));
        return new SignUpResponse(result.organizationId(), result.administratorId());
    }
}
