package pe.buildshield.core.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.core.iam.application.UserManagementService;
import pe.buildshield.core.iam.domain.model.Role;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios", description = "Gestión de usuarios y roles de la organización (solo administrador)")
@SecurityRequirement(name = "bearer")
class UsersController {

    private final UserManagementService userManagement;

    UsersController(UserManagementService userManagement) {
        this.userManagement = userManagement;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Crear un usuario de la organización",
            description = "El usuario queda en la organización del administrador que lo crea.")
    @ApiResponse(responseCode = "201", description = "Usuario creado",
            content = @Content(schema = @Schema(implementation = UserResource.class),
                    examples = @ExampleObject(value = IamApiExamples.USER)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos (correo, rol o contraseña)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Solo un administrador puede crear usuarios",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Correo ya registrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<UserResource> create(@Valid @RequestBody CreateUserRequest request) {
        UserManagementService.UserView created = userManagement.create(new UserManagementService.CreateUserCommand(
                request.fullName(), request.email(), request.role(), request.password()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(UserResource.of(created));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Listar los usuarios de la organización",
            description = "Todos los usuarios de la organización del administrador, activos e inactivos.")
    @ApiResponse(responseCode = "200", description = "Usuarios de la organización del administrador",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResource.class)),
                    examples = @ExampleObject(value = IamApiExamples.USERS)))
    @ApiResponse(responseCode = "403", description = "Solo un administrador puede ver los usuarios",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    List<UserResource> list() {
        return userManagement.list().stream().map(UserResource::of).toList();
    }

    @Schema(description = "Datos del nuevo usuario")
    record CreateUserRequest(
            @Schema(example = "Rosa Quispe") @NotBlank @Size(max = 150) String fullName,
            @Schema(example = "rosa@andina.pe") @NotBlank @Size(max = 254) String email,
            @Schema(example = "WAREHOUSE_MANAGER", allowableValues = {"ADMINISTRATOR", "WAREHOUSE_MANAGER", "SITE_MANAGER"})
            @NotBlank String role,
            @Schema(description = "Contraseña inicial: 8 a 72 caracteres, con letra y número", example = "Almacen123")
            @NotBlank String password) {
    }

    @Schema(description = "Usuario de la organización")
    record UserResource(UUID id, String email, String fullName, Role role, boolean active) {

        static UserResource of(UserManagementService.UserView view) {
            return new UserResource(view.id(), view.email(), view.fullName(), view.role(), view.active());
        }
    }
}
