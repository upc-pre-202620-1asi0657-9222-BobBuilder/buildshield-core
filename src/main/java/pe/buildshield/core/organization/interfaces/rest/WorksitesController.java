package pe.buildshield.core.organization.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.core.shared.error.ErrorResponse;
import pe.buildshield.core.organization.application.WorksiteService;
import pe.buildshield.core.organization.application.WorksiteService.WorksiteView;
import pe.buildshield.core.organization.domain.model.Location;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/worksites")
@Tag(name = "Obras", description = "US14: obras de la organización con su ubicación y fechas")
@SecurityRequirement(name = "bearer")
class WorksitesController {

    private final WorksiteService worksites;

    WorksitesController(WorksiteService worksites) {
        this.worksites = worksites;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Registrar una obra",
            description = "Nombre, ubicación (dirección, distrito, ciudad y coordenadas opcionales) y fechas de inicio y fin.")
    @ApiResponse(responseCode = "201", description = "Obra registrada",
            content = @Content(schema = @Schema(implementation = WorksiteResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.WORKSITE)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos; la fecha de fin no puede ser anterior a la de inicio",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Solo el administrador registra obras",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<WorksiteResource> register(@Valid @RequestBody RegisterWorksiteRequest request) {
        WorksiteView created = worksites.register(new WorksiteService.RegisterWorksite(request.name(),
                new Location(request.address(), request.district(), request.city(), request.latitude(), request.longitude()),
                request.startDate(), request.endDate()));
        return ResponseEntity.created(URI.create("/api/v1/worksites/" + created.id())).body(WorksiteResource.of(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar obras", description = "El administrador ve todas; el encargado de obra, solo las asignadas.")
    @ApiResponse(responseCode = "200", description = "Obras visibles para el usuario",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorksiteResource.class)),
                    examples = @ExampleObject(value = OrganizationApiExamples.WORKSITES)))
    List<WorksiteResource> list() {
        return worksites.list().stream().map(WorksiteResource::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar una obra", description = "Datos de una obra visible para el usuario.")
    @ApiResponse(responseCode = "200", description = "Obra",
            content = @Content(schema = @Schema(implementation = WorksiteResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.WORKSITE)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o no está asignada al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    WorksiteResource get(@Parameter(description = "Id de la obra", example = OrganizationApiExamples.WORKSITE_ID) @PathVariable UUID id) {
        return WorksiteResource.of(worksites.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Modificar una obra", description = "Solo cambian los campos enviados.")
    @ApiResponse(responseCode = "200", description = "Obra modificada",
            content = @Content(schema = @Schema(implementation = WorksiteResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.WORKSITE)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos o rango de fechas inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe o es de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    WorksiteResource update(@Parameter(description = "Id de la obra", example = OrganizationApiExamples.WORKSITE_ID) @PathVariable UUID id, @Valid @RequestBody UpdateWorksiteRequest request) {
        return WorksiteResource.of(worksites.update(id, new WorksiteService.UpdateWorksite(request.name(),
                request.address(), request.district(), request.city(), request.latitude(), request.longitude(),
                request.startDate(), request.endDate())));
    }

    @Schema(description = "Datos de la obra")
    record RegisterWorksiteRequest(
            @Schema(example = "Torre Norte") @NotBlank @Size(max = 150) String name,
            @Schema(example = "Av. Javier Prado 123") @NotBlank @Size(max = 200) String address,
            @Schema(example = "San Isidro") @NotBlank @Size(max = 100) String district,
            @Schema(example = "Lima") @NotBlank @Size(max = 100) String city,
            @Schema(description = "Opcional, junto con la longitud", example = "-12.0931") Double latitude,
            @Schema(description = "Opcional, junto con la latitud", example = "-77.0465") Double longitude,
            @Schema(example = "2026-11-01") @NotNull LocalDate startDate,
            @Schema(description = "Opcional; no puede ser anterior al inicio", example = "2027-06-30") LocalDate endDate) {
    }

    @Schema(description = "Cambios de la obra; los campos ausentes no cambian")
    record UpdateWorksiteRequest(
            @Size(max = 150) String name,
            @Size(max = 200) String address,
            @Size(max = 100) String district,
            @Size(max = 100) String city,
            Double latitude,
            Double longitude,
            LocalDate startDate,
            LocalDate endDate) {
    }

    @Schema(description = "Obra")
    record WorksiteResource(UUID id, String name, String address, String district, String city, Double latitude,
            Double longitude, LocalDate startDate, LocalDate endDate) {

        static WorksiteResource of(WorksiteView view) {
            Location location = view.location();
            return new WorksiteResource(view.id(), view.name(), location.address(), location.district(),
                    location.city(), location.latitude(), location.longitude(), view.startDate(), view.endDate());
        }
    }
}
