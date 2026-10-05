package pe.buildshield.core.ordering.interfaces.rest;

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
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.core.shared.error.ErrorResponse;
import pe.buildshield.core.ordering.application.OrderService;
import pe.buildshield.core.ordering.application.OrderService.LineView;
import pe.buildshield.core.ordering.application.OrderService.OrderView;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Pedidos", description = "US18 crear, US20 aprobar o rechazar, US21 consultar estado")
@SecurityRequirement(name = "bearer")
class OrdersController {

    private final OrderService orders;

    OrdersController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @PreAuthorize("hasRole('SITE_MANAGER')")
    @Operation(summary = "Crear un pedido de materiales",
            description = "El encargado de obra pide para una obra asignada a un almacén activo. Queda Registrado.")
    @ApiResponse(responseCode = "201", description = "Pedido registrado",
            content = @Content(schema = @Schema(implementation = OrderResource.class),
                    examples = @ExampleObject(value = OrderApiExamples.ORDER_REGISTERED)))
    @ApiResponse(responseCode = "400", description = "Sin líneas, cantidades no positivas o material repetido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Solo el encargado de obra crea pedidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Obra no asignada, almacén o material inexistentes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Almacén desactivado o material retirado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<OrderResource> place(@Valid @RequestBody PlaceOrderRequest request) {
        OrderView created = orders.place(new OrderService.PlaceOrder(request.worksiteId(), request.warehouseId(),
                request.notes(), request.lines().stream()
                .map(line -> new OrderService.LineRequest(line.materialId(), line.quantity())).toList()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(OrderResource.of(created));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar pedidos",
            description = "Administrador: todos; encargado de obra: los de sus obras; encargado de almacén: los de sus almacenes.")
    @ApiResponse(responseCode = "200", description = "Pedidos visibles para el usuario",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderResource.class)),
                    examples = @ExampleObject(value = OrderApiExamples.ORDERS)))
    List<OrderResource> list() {
        return orders.list().stream().map(OrderResource::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar el estado de un pedido",
            description = "Estado y, por material, lo solicitado, despachado, recibido y pendiente.")
    @ApiResponse(responseCode = "200", description = "Pedido",
            content = @Content(schema = @Schema(implementation = OrderResource.class),
                    examples = @ExampleObject(value = OrderApiExamples.ORDER_REGISTERED)))
    @ApiResponse(responseCode = "404", description = "No existe, es de otra organización o no corresponde al usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    OrderResource get(@Parameter(description = "Id del pedido", example = OrderApiExamples.ORDER_ID) @PathVariable UUID id) {
        return OrderResource.of(orders.get(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Aprobar un pedido", description = "Registrado → EnRevision. Lo hace el encargado del almacén de origen o el administrador.")
    @ApiResponse(responseCode = "200", description = "Pedido aprobado",
            content = @Content(schema = @Schema(implementation = OrderResource.class),
                    examples = @ExampleObject(value = OrderApiExamples.ORDER_APPROVED)))
    @ApiResponse(responseCode = "404", description = "No existe o el usuario no es del almacén de origen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El estado actual no permite aprobar",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    OrderResource approve(@Parameter(description = "Id del pedido", example = OrderApiExamples.ORDER_ID) @PathVariable UUID id) {
        return OrderResource.of(orders.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @Operation(summary = "Rechazar un pedido", description = "Registrado → Cancelado. El motivo es obligatorio.")
    @ApiResponse(responseCode = "200", description = "Pedido rechazado",
            content = @Content(schema = @Schema(implementation = OrderResource.class),
                    examples = @ExampleObject(value = OrderApiExamples.ORDER_REJECTED)))
    @ApiResponse(responseCode = "400", description = "Falta el motivo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe o el usuario no es del almacén de origen",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El estado actual no permite rechazar",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    OrderResource reject(@Parameter(description = "Id del pedido", example = OrderApiExamples.ORDER_ID) @PathVariable UUID id, @RequestBody(required = false) RejectOrderRequest request) {
        return OrderResource.of(orders.reject(id, request == null ? null : request.reason()));
    }

    @Schema(description = "Pedido de materiales")
    record PlaceOrderRequest(
            @NotNull UUID worksiteId,
            @Schema(description = "Almacén al que se pide") @NotNull UUID warehouseId,
            @Size(max = 500) String notes,
            @NotEmpty List<@Valid LineRequest> lines) {
    }

    @Schema(description = "Material y cantidad solicitada")
    record LineRequest(
            @NotNull UUID materialId,
            @Schema(example = "50") @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
    }

    @Schema(description = "Motivo del rechazo")
    record RejectOrderRequest(@Schema(example = "No hay transporte disponible esta semana") String reason) {
    }

    @Schema(description = "Pedido con su estado y cantidades por material")
    record OrderResource(UUID id, UUID worksiteId, UUID warehouseId, UUID requestedBy,
            @Schema(example = "IN_REVIEW") String status,
            @Schema(description = "Estado en español", example = "EnRevision") String statusLabel,
            String notes, String rejectionReason, Instant placedAt, UUID decidedBy, Instant decidedAt,
            List<LineResource> lines) {

        static OrderResource of(OrderView view) {
            return new OrderResource(view.id(), view.worksiteId(), view.warehouseId(), view.requestedBy(),
                    view.status().name(), view.status().label(), view.notes(), view.rejectionReason(), view.placedAt(),
                    view.decidedBy(), view.decidedAt(), view.lines().stream().map(LineResource::of).toList());
        }
    }

    @Schema(description = "Línea del pedido: pendiente = solicitado - despachado - cancelado")
    record LineResource(UUID materialId, String sku, String unit, BigDecimal requested, BigDecimal dispatched,
            BigDecimal cancelled, BigDecimal received, BigDecimal pending) {

        static LineResource of(LineView line) {
            return new LineResource(line.materialId(), line.sku(), line.unit(), line.requested(), line.dispatched(),
                    line.cancelled(), line.received(), line.pending());
        }
    }
}
