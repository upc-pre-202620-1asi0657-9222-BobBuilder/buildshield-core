package pe.buildshield.core.organization.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.core.organization.application.AssignmentService;
import pe.buildshield.core.organization.application.AssignmentService.AssignmentView;
import pe.buildshield.core.organization.domain.model.SiteType;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
@Tag(name = "Asignaciones", description = "US17: asignación de encargados a obras y almacenes; define qué ve cada usuario")
@SecurityRequirement(name = "bearer")
class AssignmentsController {

    private final AssignmentService assignments;

    AssignmentsController(AssignmentService assignments) {
        this.assignments = assignments;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Asignar un encargado a una obra o almacén",
            description = "El encargado de obra va a obras; el de almacén, a almacenes activos.")
    @ApiResponse(responseCode = "201", description = "Asignación creada")
    @ApiResponse(responseCode = "400", description = "El rol del usuario no corresponde al tipo de lugar",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario, obra o almacén inexistentes o de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Asignación activa repetida o almacén desactivado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<AssignmentResource> assign(@Valid @RequestBody AssignRequest request) {
        AssignmentView created = assignments.assign(
                new AssignmentService.AssignStaff(request.userId(), request.siteType(), request.siteId()));
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + created.id()))
                .body(AssignmentResource.of(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar asignaciones (vigentes y terminadas)",
            description = "El administrador ve todas; cada encargado, solo las suyas.")
    List<AssignmentResource> list() {
        return assignments.list().stream().map(AssignmentResource::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar una asignación")
    @ApiResponse(responseCode = "200", description = "Asignación")
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    AssignmentResource get(@PathVariable UUID id) {
        return AssignmentResource.of(assignments.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Terminar una asignación", description = "Envía active=false. La asignación queda en el historial.")
    @ApiResponse(responseCode = "200", description = "Asignación terminada")
    @ApiResponse(responseCode = "404", description = "No existe o es de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La asignación ya había terminado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    AssignmentResource update(@PathVariable UUID id, @Valid @RequestBody UpdateAssignmentRequest request) {
        return AssignmentResource.of(assignments.update(id, new AssignmentService.UpdateAssignment(request.active())));
    }

    @Schema(description = "Encargado y lugar")
    record AssignRequest(
            @NotNull UUID userId,
            @Schema(example = "WORKSITE") @NotNull SiteType siteType,
            @Schema(description = "Id de la obra o del almacén") @NotNull UUID siteId) {
    }

    @Schema(description = "Cambio de la asignación")
    record UpdateAssignmentRequest(@Schema(description = "false termina la asignación", example = "false") Boolean active) {
    }

    @Schema(description = "Asignación de un encargado")
    record AssignmentResource(UUID id, UUID userId, SiteType siteType, UUID siteId, Instant assignedAt, Instant endedAt,
            boolean active) {

        static AssignmentResource of(AssignmentView view) {
            return new AssignmentResource(view.id(), view.userId(), view.siteType(), view.siteId(), view.assignedAt(),
                    view.endedAt(), view.active());
        }
    }
}
