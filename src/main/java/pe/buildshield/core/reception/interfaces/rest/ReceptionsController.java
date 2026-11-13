package pe.buildshield.core.reception.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.core.reception.application.ReceptionService;
import pe.buildshield.core.reception.application.ReceptionService.ComparisonLine;
import pe.buildshield.core.reception.application.ReceptionService.ComparisonView;
import pe.buildshield.core.reception.application.ReceptionService.LineView;
import pe.buildshield.core.reception.application.ReceptionService.ReceptionView;
import pe.buildshield.core.shared.error.ErrorResponse;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receptions")
@Tag(name = "Recepciones", description = "US35 recepción y cotejo, US37 conformidad")
@SecurityRequirement(name = "bearer")
class ReceptionsController {

    private final ReceptionService receptions;

    ReceptionsController(ReceptionService receptions) {
        this.receptions = receptions;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SITE_MANAGER')")
    @Operation(summary = "Abrir la recepción de un despacho",
            description = "El administrador o el encargado de la obra de destino abre la recepción de un despacho en "
                    + "tránsito; queda EnCurso con una línea por cada línea del despacho. Hay una sola recepción por "
                    + "despacho: si ya existe responde 409 RECEPTION_ALREADY_EXISTS con su id en los detalles.")
    @ApiResponse(responseCode = "201", description = "Recepción abierta",
            content = @Content(schema = @Schema(implementation = ReceptionResource.class),
                    examples = @ExampleObject(value = ReceptionApiExamples.RECEPTION_STARTED)))
    @ApiResponse(responseCode = "404", description = "El despacho no existe o no va a una obra del usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Ya existe la recepción (RECEPTION_ALREADY_EXISTS) o el despacho "
            + "no está en tránsito (DISPATCH_NOT_IN_TRANSIT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<ReceptionResource> start(@Valid @RequestBody StartReceptionRequest request) {
        ReceptionView started = receptions.start(request.dispatchId());
        return ResponseEntity.created(URI.create("/api/v1/receptions/" + started.id()))
                .body(ReceptionResource.of(started));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar una recepción",
            description = "Estado y lo recibido por línea. La ven el administrador y los encargados de la obra de "
                    + "destino y del almacén de origen.")
    @ApiResponse(responseCode = "200", description = "Recepción",
            content = @Content(schema = @Schema(implementation = ReceptionResource.class),
                    examples = @ExampleObject(value = ReceptionApiExamples.RECEPTION_RECORDED)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o no corresponde al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ReceptionResource get(@Parameter(description = "Id de la recepción", example = ReceptionApiExamples.RECEPTION_ID)
            @PathVariable UUID id) {
        return ReceptionResource.of(receptions.get(id));
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SITE_MANAGER')")
    @Operation(summary = "Registrar lo recibido de una línea",
            description = "US35: cantidad recibida (cero o más, sin superar lo despachado). Se guarda de a poco y se "
                    + "puede corregir mientras la recepción está EnCurso; calcula la merma de la línea.")
    @ApiResponse(responseCode = "200", description = "Línea registrada",
            content = @Content(schema = @Schema(implementation = ReceptionResource.class),
                    examples = @ExampleObject(value = ReceptionApiExamples.RECEPTION_RECORDED)))
    @ApiResponse(responseCode = "400", description = "Cantidad negativa, con más de 3 decimales o mayor que lo "
            + "despachado (RECEIVED_EXCEEDS_DISPATCHED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La recepción o la línea no existen o no corresponden al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La recepción ya fue confirmada (RECEPTION_ALREADY_CONFIRMED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ReceptionResource recordLine(
            @Parameter(description = "Id de la recepción", example = ReceptionApiExamples.RECEPTION_ID) @PathVariable UUID id,
            @Parameter(description = "Id de la línea de la recepción", example = ReceptionApiExamples.LINE_ID)
            @PathVariable UUID lineId,
            @Valid @RequestBody RecordLineRequest request) {
        return ReceptionResource.of(receptions.recordLine(id, lineId, request.receivedQty()));
    }

    @GetMapping("/{id}/comparison")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cotejar lo pedido, despachado y recibido",
            description = "US35, QAS01: por material, lo solicitado, despachado y recibido, la diferencia (despachado - "
                    + "recibido), la merma en % y si está dentro de la tolerancia del material.")
    @ApiResponse(responseCode = "200", description = "Cotejo por material",
            content = @Content(schema = @Schema(implementation = ComparisonResource.class),
                    examples = @ExampleObject(value = ReceptionApiExamples.COMPARISON)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o no corresponde al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ComparisonResource comparison(
            @Parameter(description = "Id de la recepción", example = ReceptionApiExamples.RECEPTION_ID) @PathVariable UUID id) {
        return ComparisonResource.of(receptions.comparison(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SITE_MANAGER')")
    @Operation(summary = "Dar conformidad a la recepción",
            description = "US37, QAS10: exige lo recibido de todas las líneas. En una transacción suma lo recibido al "
                    + "stock de la obra, lo registra en el pedido, marca la recepción Confirmada y el despacho Recibido. "
                    + "Requiere Idempotency-Key: repetir con la misma clave devuelve la misma respuesta; otra clave "
                    + "responde 409 RECEPTION_ALREADY_CONFIRMED.")
    @ApiResponse(responseCode = "200", description = "Recepción confirmada",
            content = @Content(schema = @Schema(implementation = ReceptionResource.class),
                    examples = @ExampleObject(value = ReceptionApiExamples.RECEPTION_CONFIRMED)))
    @ApiResponse(responseCode = "404", description = "No existe o el usuario no es de la obra de destino",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Faltan líneas por registrar (RECEPTION_INCOMPLETE) o ya fue "
            + "confirmada (RECEPTION_ALREADY_CONFIRMED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ReceptionResource confirm(@Parameter(description = "Id de la recepción", example = ReceptionApiExamples.RECEPTION_ID)
            @PathVariable UUID id) {
        return ReceptionResource.of(receptions.confirm(id));
    }

    @Schema(description = "Despacho a recibir")
    record StartReceptionRequest(
            @Schema(example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f") @NotNull UUID dispatchId) {
    }

    @Schema(description = "Cantidad recibida de la línea")
    record RecordLineRequest(
            @Schema(example = "29.5") @NotNull @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal receivedQty) {
    }

    @Schema(description = "Recepción con lo recibido por línea")
    record ReceptionResource(UUID id, UUID dispatchId, UUID orderId, UUID warehouseId, UUID worksiteId,
            @Schema(example = "IN_PROGRESS") String status,
            @Schema(description = "Estado en español", example = "EnCurso") String statusLabel,
            UUID confirmedBy, Instant confirmedAt, List<LineView> lines) {

        static ReceptionResource of(ReceptionView view) {
            return new ReceptionResource(view.id(), view.dispatchId(), view.orderId(), view.warehouseId(),
                    view.worksiteId(), view.status().name(), view.status().label(), view.confirmedBy(),
                    view.confirmedAt(), view.lines());
        }
    }

    @Schema(description = "Cotejo por material: complete indica que todas las líneas tienen lo recibido; "
            + "withinTolerance, que además ninguna supera la tolerancia")
    record ComparisonResource(UUID receptionId, UUID dispatchId, UUID orderId, String status, String statusLabel,
            boolean complete, boolean withinTolerance, List<ComparisonLine> lines) {

        static ComparisonResource of(ComparisonView view) {
            return new ComparisonResource(view.receptionId(), view.dispatchId(), view.orderId(), view.status().name(),
                    view.status().label(), view.complete(), view.withinTolerance(), view.lines());
        }
    }
}
