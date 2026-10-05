package pe.buildshield.core.inventory.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.commons.error.ErrorResponse;
import pe.buildshield.core.inventory.StockService.StockLevel;
import pe.buildshield.core.inventory.application.StockQueries;
import pe.buildshield.core.inventory.application.StockQueries.StockView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
@Tag(name = "Stock", description = "Existencias por almacén y material")
@SecurityRequirement(name = "bearer")
class StockController {

    private final StockQueries stock;

    StockController(StockQueries stock) {
        this.stock = stock;
    }

    @PostMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WAREHOUSE_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una entrada de material (carga de existencias)",
            description = "El administrador o un encargado asignado al almacén. Suma a lo disponible.")
    @ApiResponse(responseCode = "201", description = "Entrada registrada; devuelve el saldo")
    @ApiResponse(responseCode = "400", description = "Cantidad inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Almacén o material inexistente, de otra organización o no asignado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Almacén desactivado o material retirado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    StockLevelResource registerEntry(@Valid @RequestBody StockEntryRequest request) {
        return StockLevelResource.of(stock.registerEntry(request.warehouseId(), request.materialId(), request.quantity(),
                request.note()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar el stock",
            description = "Administrador: todos los almacenes; encargado de almacén: los suyos; encargado de obra: los activos.")
    List<StockView> list(@Parameter(description = "Filtra por almacén") @RequestParam(required = false) UUID warehouseId) {
        return stock.list(warehouseId);
    }

    @Schema(description = "Entrada de material a un almacén")
    record StockEntryRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID materialId,
            @Schema(example = "100") @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @Schema(description = "Guía, proveedor u observación", example = "Guía 001-2345")
            @Size(max = 200) String note) {
    }

    @Schema(description = "Saldo del material en el almacén")
    record StockLevelResource(UUID warehouseId, UUID materialId, BigDecimal availableQty, BigDecimal reservedQty) {

        static StockLevelResource of(StockLevel level) {
            return new StockLevelResource(level.locationId(), level.materialId(), level.availableQty(), level.reservedQty());
        }
    }
}
