package pe.buildshield.core.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.commons.security.AuthenticatedUser;
import pe.buildshield.core.iam.application.AuthenticationService;
import pe.buildshield.core.iam.application.PasswordResetService;
import pe.buildshield.core.iam.application.SignUpService;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.PasswordResetConfirmRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.PasswordResetRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.RefreshRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.SignInRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.SignOutRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.SignUpRequest;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.SignUpResponse;
import pe.buildshield.core.iam.interfaces.rest.AuthResources.TokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro de organizaciones, sesiones y recuperación de contraseña")
class AuthController {

    private final SignUpService signUpService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;

    AuthController(SignUpService signUpService, AuthenticationService authenticationService,
            PasswordResetService passwordResetService) {
        this.signUpService = signUpService;
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
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

    @PostMapping("/sign-in")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Iniciar sesión", description = "Devuelve un token de acceso de 15 minutos y un token de renovación.")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada")
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    TokenResponse signIn(@Valid @RequestBody SignInRequest request) {
        return TokenResponse.from(authenticationService.signIn(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Renovar la sesión",
            description = "Entrega tokens nuevos; el token de renovación usado queda revocado (rotación).")
    @ApiResponse(responseCode = "200", description = "Sesión renovada")
    @ApiResponse(responseCode = "401", description = "Token de renovación inválido, vencido o revocado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/sign-out")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Cerrar sesión",
            description = "Revoca el token de renovación y el token de acceso usado, hasta que este venza.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    @ApiResponse(responseCode = "401", description = "Sin token de acceso válido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    void signOut(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) SignOutRequest request) {
        authenticationService.signOut(user.tenant().userId(), user.tokenId(),
                request == null ? null : request.refreshToken());
    }

    @PostMapping("/password-reset")
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicitar la recuperación de contraseña",
            description = "Si el correo está registrado, envía un enlace válido por 30 minutos. "
                    + "La respuesta es la misma exista o no el correo.")
    @ApiResponse(responseCode = "202", description = "Solicitud recibida")
    void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @PreAuthorize("permitAll()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Definir la nueva contraseña con el enlace recibido",
            description = "El enlace se usa una sola vez; además se cierran las sesiones abiertas del usuario.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", description = "Enlace inválido, vencido o ya usado, o contraseña insegura",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
    }
}
