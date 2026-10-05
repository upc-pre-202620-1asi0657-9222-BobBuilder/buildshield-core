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
import pe.buildshield.core.organization.application.WarehouseService;
import pe.buildshield.core.organization.application.WarehouseService.WarehouseView;
import pe.buildshield.core.organization.domain.model.WarehouseType;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@Tag(name = "Almacenes", description = "US15: almacenes y centros de acopio; se desactivan sin perder historial")
@SecurityRequirement(name = "bearer")
class WarehousesController {

    private final WarehouseService warehouses;

    WarehousesController(WarehouseService warehouses) {
        this.warehouses = warehouses;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Registrar un almacén o centro de acopio",
            description = "Nombre, tipo (WAREHOUSE o COLLECTION_CENTER) y dirección. Queda activo.")
    @ApiResponse(responseCode = "201", description = "Registrado y activo",
            content = @Content(schema = @Schema(implementation = WarehouseResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.WAREHOUSE)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Solo el administrador registra almacenes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<WarehouseResource> register(@Valid @RequestBody RegisterWarehouseRequest request) {
        WarehouseView created = warehouses.register(
                new WarehouseService.RegisterWarehouse(request.name(), request.type(), request.address()));
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + created.id())).body(WarehouseResource.of(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar almacenes",
            description = "El administrador ve todos; el encargado de almacén, los asignados; el encargado de obra, los activos.")
    @ApiResponse(responseCode = "200", description = "Almacenes visibles para el usuario",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = WarehouseResource.class)),
                    examples = @ExampleObject(value = OrganizationApiExamples.WAREHOUSES)))
    List<WarehouseResource> list() {
        return warehouses.list().stream().map(WarehouseResource::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar un almacén", description = "Datos de un almacén visible para el usuario, activo o desactivado.")
    @ApiResponse(responseCode = "200", description = "Almacén",
            content = @Content(schema = @Schema(implementation = WarehouseResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.WAREHOUSE)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o el usuario no tiene acceso",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    WarehouseResource get(@Parameter(description = "Id del almacén", example = OrganizationApiExamples.WAREHOUSE_ID) @PathVariable UUID id) {
        return WarehouseResource.of(warehouses.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Modificar, desactivar o reactivar un almacén",
            description = "Solo cambian los campos enviados. active=false lo desactiva; active=true lo reactiva.")
    @ApiResponse(responseCode = "200", description = "Almacén modificado",
            content = @Content(schema = @Schema(implementation = WarehouseResource.class),
                    examples = @ExampleObject(value = OrganizationApiExamples.WAREHOUSE)))
    @ApiResponse(responseCode = "404", description = "No existe o es de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    WarehouseResource update(@Parameter(description = "Id del almacén", example = OrganizationApiExamples.WAREHOUSE_ID) @PathVariable UUID id, @Valid @RequestBody UpdateWarehouseRequest request) {
        return WarehouseResource.of(warehouses.update(id, new WarehouseService.UpdateWarehouse(
                request.name(), request.type(), request.address(), request.active())));
    }

    @Schema(description = "Datos del almacén o centro de acopio")
    record RegisterWarehouseRequest(
            @Schema(example = "Almacén Central") @NotBlank @Size(max = 150) String name,
            @Schema(example = "WAREHOUSE") @NotNull WarehouseType type,
            @Schema(example = "Av. Argentina 2500, Callao") @NotBlank @Size(max = 200) String address) {
    }

    @Schema(description = "Cambios del almacén; los campos ausentes no cambian")
    record UpdateWarehouseRequest(
            @Size(max = 150) String name,
            WarehouseType type,
            @Size(max = 200) String address,
            @Schema(description = "false desactiva, true reactiva") Boolean active) {
    }

    @Schema(description = "Almacén o centro de acopio")
    record WarehouseResource(UUID id, String name, WarehouseType type, String address, boolean active) {

        static WarehouseResource of(WarehouseView view) {
            return new WarehouseResource(view.id(), view.name(), view.type(), view.address(), view.active());
        }
    }
}
