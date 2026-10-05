package pe.buildshield.core.audit.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.buildshield.core.audit.AuditTrail;
import java.util.List;
import java.util.UUID;

@RestController @Validated
@RequestMapping("/api/v1/audit/events")
@SecurityRequirement(name = "bearer")
@Tag(name = "Auditoría", description = "Historial de solo anexado, limitado a la empresa del administrador")
class AuditController {
    private final AuditTrail audit;
    AuditController(AuditTrail audit) { this.audit = audit; }
    @GetMapping @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Consultar el historial de la empresa", description = "Solo el administrador ve los eventos de su organización; paginación estable por fecha e id descendentes.")
    @ApiResponse(responseCode = "200", description = "Eventos ordenados por fecha e identificador descendentes",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AuditTrail.Event.class)),
                    examples = @ExampleObject(value = "[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"action\":\"STOCK_ADDED\",\"resourceType\":\"STOCK_ITEM\",\"occurredAt\":\"2026-10-05T12:00:00Z\",\"details\":{\"quantity\":10}}]")))
    @ApiResponse(responseCode = "400", description = "Filtros o paginación inválidos")
    @ApiResponse(responseCode = "403", description = "Solo el administrador consulta el historial")
    List<AuditTrail.Event> list(
            @Parameter(description = "Tipo de recurso", example = "ORDER") @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,40}") String resourceType,
            @Parameter(description = "Id del recurso", example = "00000000-0000-0000-0000-000000000001") @RequestParam(required = false) UUID resourceId,
            @Parameter(description = "Página desde cero", example = "0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página entre 1 y 100", example = "50") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return audit.list(resourceType, resourceId, page, size);
    }
}
