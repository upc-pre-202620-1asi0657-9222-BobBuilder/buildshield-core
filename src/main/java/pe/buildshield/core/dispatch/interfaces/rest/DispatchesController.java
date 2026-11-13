package pe.buildshield.core.dispatch.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.core.dispatch.application.DispatchService;
import pe.buildshield.core.dispatch.application.DispatchService.CarrierView;
import pe.buildshield.core.dispatch.application.DispatchService.DispatchView;
import pe.buildshield.core.dispatch.application.DispatchService.LineView;
import pe.buildshield.core.dispatch.application.DispatchService.ManifestLine;
import pe.buildshield.core.dispatch.application.DispatchService.ManifestSite;
import pe.buildshield.core.dispatch.application.DispatchService.ManifestView;
import pe.buildshield.core.dispatch.application.DispatchService.WeighingView;
import pe.buildshield.core.dispatch.domain.model.DispatchStatus;
import pe.buildshield.core.shared.error.ErrorResponse;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatches")
@Tag(name = "Despachos", description = "US22 generar, US23 partición, US24 manifiesto, US27 pesaje de salida, US30 transportista")
@SecurityRequirement(name = "bearer")
class DispatchesController {

    private final DispatchService dispatches;

    DispatchesController(DispatchService dispatches) {
        this.dispatches = dispatches;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Generar un despacho de un pedido aprobado",
            description = "El administrador o el encargado del almacén de origen despacha una o varias líneas del pedido "
                    + "(US22). Un pedido admite varios despachos (US23): cada cantidad es mayor que cero y no supera lo "
                    + "pendiente de su línea. Lo despachado se suma al pedido, que pasa a ParcialmenteAtendido o Atendido. "
                    + "Se genera el código único del manifiesto. Queda Preparado.")
    @ApiResponse(responseCode = "201", description = "Despacho preparado",
            content = @Content(schema = @Schema(implementation = DispatchResource.class),
                    examples = @ExampleObject(value = DispatchApiExamples.DISPATCH_PREPARED)))
    @ApiResponse(responseCode = "400", description = "Sin líneas, línea repetida o que no es del pedido, cantidad no positiva",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El pedido no existe o el usuario no es del almacén de origen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Pedido no aprobado (ORDER_NOT_DISPATCHABLE), cantidad mayor que lo "
            + "pendiente (DISPATCH_EXCEEDS_PENDING) o despacho simultáneo del mismo pedido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<DispatchResource> create(@Valid @RequestBody CreateDispatchRequest request) {
        DispatchView created = dispatches.create(new DispatchService.CreateDispatch(request.orderId(),
                request.lines().stream().map(line -> new DispatchService.LineRequest(line.orderLineId(), line.quantity()))
                        .toList()));
        return ResponseEntity.created(URI.create("/api/v1/dispatches/" + created.id())).body(DispatchResource.of(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar despachos",
            description = "Administrador: todos; encargado de almacén: los de sus almacenes; encargado de obra: los que "
                    + "van a sus obras. Se filtran por pedido y por estado.")
    @ApiResponse(responseCode = "200", description = "Despachos visibles para el usuario",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DispatchResource.class)),
                    examples = @ExampleObject(value = DispatchApiExamples.DISPATCHES)))
    @ApiResponse(responseCode = "400", description = "Estado o id con formato incorrecto",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    List<DispatchResource> list(
            @Parameter(description = "Id del pedido; si se omite, todos", example = DispatchApiExamples.ORDER_ID)
            @RequestParam(required = false) UUID orderId,
            @Parameter(description = "Estado: PREPARED, IN_TRANSIT, RECEIVED o CANCELLED", example = "IN_TRANSIT")
            @RequestParam(required = false) DispatchStatus status) {
        return dispatches.list(orderId, status).stream().map(DispatchResource::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar un despacho",
            description = "Estado, líneas, transportista y pesaje de salida. Lo ven el administrador y los encargados del "
                    + "almacén de origen y de la obra de destino.")
    @ApiResponse(responseCode = "200", description = "Despacho",
            content = @Content(schema = @Schema(implementation = DispatchResource.class),
                    examples = @ExampleObject(value = DispatchApiExamples.DISPATCH_IN_TRANSIT)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o no corresponde al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    DispatchResource get(@Parameter(description = "Id del despacho", example = DispatchApiExamples.DISPATCH_ID)
            @PathVariable UUID id) {
        return DispatchResource.of(dispatches.get(id));
    }

    @PatchMapping("/{id}/carrier")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Registrar el transportista",
            description = "US30: nombre, documento y placa del vehículo. Solo mientras el despacho está Preparado; se "
                    + "puede corregir hasta que salga.")
    @ApiResponse(responseCode = "200", description = "Transportista registrado",
            content = @Content(schema = @Schema(implementation = DispatchResource.class),
                    examples = @ExampleObject(value = DispatchApiExamples.DISPATCH_WITH_CARRIER)))
    @ApiResponse(responseCode = "400", description = "Nombre, documento o placa inválidos (INVALID_CARRIER)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe o el usuario no es del almacén de origen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El despacho ya salió (INVALID_DISPATCH_TRANSITION)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    DispatchResource assignCarrier(@Parameter(description = "Id del despacho", example = DispatchApiExamples.DISPATCH_ID)
            @PathVariable UUID id, @Valid @RequestBody CarrierRequest request) {
        return DispatchResource.of(dispatches.assignCarrier(id, request.carrierName(), request.carrierDocument(),
                request.plate()));
    }

    @PostMapping("/{id}/departure-weighing")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Registrar el pesaje de salida",
            description = "US27: peso bruto y tara en kg; el neto (bruto - tara) debe ser mayor que cero. Uno por "
                    + "despacho y solo mientras está Preparado. La foto del ticket es una URL http(s) de la evidencia.")
    @ApiResponse(responseCode = "200", description = "Pesaje registrado",
            content = @Content(schema = @Schema(implementation = DispatchResource.class),
                    examples = @ExampleObject(value = DispatchApiExamples.DISPATCH_WEIGHED)))
    @ApiResponse(responseCode = "400", description = "Pesos inválidos o tara mayor o igual al bruto (INVALID_WEIGHING), "
            + "URL de evidencia inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe o el usuario no es del almacén de origen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Ya tiene pesaje (DEPARTURE_WEIGHING_ALREADY_RECORDED) o ya salió",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    DispatchResource recordDepartureWeighing(
            @Parameter(description = "Id del despacho", example = DispatchApiExamples.DISPATCH_ID) @PathVariable UUID id,
            @Valid @RequestBody WeighingRequest request) {
        return DispatchResource.of(dispatches.recordDepartureWeighing(id, request.grossKg(), request.tareKg(),
                request.ticketPhotoUrl()));
    }

    @PostMapping("/{id}/depart")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Despachar (salida del almacén)",
            description = "Preparado → EnTransito. Exige transportista y pesaje de salida. Consume la reserva de stock "
                    + "de cada línea (reservado → salida) en la misma transacción. Requiere Idempotency-Key.")
    @ApiResponse(responseCode = "200", description = "Despacho en tránsito",
            content = @Content(schema = @Schema(implementation = DispatchResource.class),
                    examples = @ExampleObject(value = DispatchApiExamples.DISPATCH_IN_TRANSIT)))
    @ApiResponse(responseCode = "404", description = "No existe o el usuario no es del almacén de origen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Falta transportista (CARRIER_REQUIRED) o pesaje "
            + "(DEPARTURE_WEIGHING_REQUIRED), ya salió o no hay reserva suficiente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    DispatchResource depart(@Parameter(description = "Id del despacho", example = DispatchApiExamples.DISPATCH_ID)
            @PathVariable UUID id) {
        return DispatchResource.of(dispatches.depart(id));
    }

    @GetMapping("/{id}/manifest")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener el manifiesto del despacho",
            description = "US24: código del manifiesto, pedido, obra, almacén, materiales con SKU, nombre y unidad, "
                    + "transportista y pesaje de salida, y un QR (PNG en base64) que codifica el código del manifiesto. "
                    + "El QR solo es un acceso rápido para ubicar el despacho.")
    @ApiResponse(responseCode = "200", description = "Manifiesto",
            content = @Content(schema = @Schema(implementation = ManifestResource.class),
                    examples = @ExampleObject(value = DispatchApiExamples.MANIFEST)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o no corresponde al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ManifestResource manifest(@Parameter(description = "Id del despacho", example = DispatchApiExamples.DISPATCH_ID)
            @PathVariable UUID id) {
        return ManifestResource.of(dispatches.manifest(id));
    }

    @Schema(description = "Despacho de una o varias líneas de un pedido")
    record CreateDispatchRequest(
            @Schema(example = DispatchApiExamples.ORDER_ID) @NotNull UUID orderId,
            @NotEmpty List<@Valid LineRequest> lines) {
    }

    @Schema(description = "Línea del pedido y cantidad a despachar")
    record LineRequest(
            @Schema(description = "Id de la línea del pedido", example = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d")
            @NotNull UUID orderLineId,
            @Schema(example = "30") @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
    }

    @Schema(description = "Transportista del despacho")
    record CarrierRequest(
            @Schema(example = "Transportes Rímac SAC") @NotBlank @Size(max = 150) String carrierName,
            @Schema(description = "DNI, RUC o carné", example = "20555666777") @NotBlank @Size(max = 20) String carrierDocument,
            @Schema(example = "ABC-123") @NotBlank @Size(max = 10) String plate) {
    }

    @Schema(description = "Pesaje de salida en kg")
    record WeighingRequest(
            @Schema(example = "2550.5") @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal grossKg,
            @Schema(example = "1050.5") @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal tareKg,
            @Schema(description = "URL http(s) de la foto del ticket de balanza (opcional)",
                    example = "https://evidencias.buildshield.pe/tickets/ticket-0042.jpg")
            @Size(max = 500) String ticketPhotoUrl) {
    }

    @Schema(description = "Despacho con su estado, líneas, transportista y pesaje de salida")
    record DispatchResource(UUID id, UUID orderId, UUID warehouseId, UUID worksiteId,
            @Schema(example = "PARTIAL") String type,
            @Schema(description = "Tipo en español", example = "Parcial") String typeLabel,
            @Schema(example = "PREPARED") String status,
            @Schema(description = "Estado en español", example = "Preparado") String statusLabel,
            @Schema(example = "MAN-20261103-7KQ2M9XA") String manifestCode,
            Instant preparedAt, Instant departedAt, Instant receivedAt, CarrierView carrier,
            WeighingView departureWeighing, List<LineView> lines) {

        static DispatchResource of(DispatchView view) {
            return new DispatchResource(view.id(), view.orderId(), view.warehouseId(), view.worksiteId(),
                    view.type().name(), view.type().label(), view.status().name(), view.status().label(),
                    view.manifestCode(), view.preparedAt(), view.departedAt(), view.receivedAt(), view.carrier(),
                    view.departureWeighing(), view.lines());
        }
    }

    @Schema(description = "Manifiesto del despacho con su QR")
    record ManifestResource(
            @Schema(example = "MAN-20261103-7KQ2M9XA") String manifestCode,
            UUID dispatchId, String status, String statusLabel, String type, String typeLabel,
            Instant preparedAt, Instant departedAt, ManifestOrder order, ManifestSite worksite, ManifestSite warehouse,
            List<ManifestLine> lines, CarrierView carrier, WeighingView departureWeighing,
            @Schema(description = "Contenido del QR: el código del manifiesto") String qrContent,
            @Schema(description = "Imagen PNG del QR en base64") String qrCodePngBase64) {

        static ManifestResource of(ManifestView view) {
            DispatchView dispatch = view.dispatch();
            return new ManifestResource(view.manifestCode(), dispatch.id(), dispatch.status().name(),
                    dispatch.status().label(), dispatch.type().name(), dispatch.type().label(), dispatch.preparedAt(),
                    dispatch.departedAt(), new ManifestOrder(view.orderId(), view.orderPlacedAt()), view.worksite(),
                    view.warehouse(), view.lines(), dispatch.carrier(), dispatch.departureWeighing(), view.qrContent(),
                    view.qrCodePngBase64());
        }
    }

    @Schema(description = "Pedido del manifiesto")
    record ManifestOrder(UUID id, Instant placedAt) {
    }
}
