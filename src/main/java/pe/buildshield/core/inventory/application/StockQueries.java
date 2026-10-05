package pe.buildshield.core.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.commons.error.ConflictException;
import pe.buildshield.commons.error.ResourceNotFoundException;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.inventory.StockService.StockLevel;
import pe.buildshield.core.inventory.domain.model.StockItem;
import pe.buildshield.core.inventory.domain.model.StockRepository;
import pe.buildshield.core.organization.OrganizationContextFacade;
import pe.buildshield.core.organization.OrganizationContextFacade.MaterialSnapshot;
import pe.buildshield.core.organization.OrganizationContextFacade.WarehouseSnapshot;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Carga de existencias y consulta de stock con la visibilidad de cada rol: el administrador ve todos
 * los almacenes; el encargado de almacén, los suyos; el encargado de obra, los activos.
 */
@Service
public class StockQueries {

    private final StockOperations operations;
    private final StockRepository stock;
    private final OrganizationContextFacade organization;

    public StockQueries(StockOperations operations, StockRepository stock, OrganizationContextFacade organization) {
        this.operations = operations;
        this.stock = stock;
        this.organization = organization;
    }

    /** Entrada de material a un almacén: el administrador o un encargado asignado a ese almacén. */
    @Transactional
    public StockLevel registerEntry(UUID warehouseId, UUID materialId, BigDecimal quantity, String note) {
        WarehouseSnapshot warehouse = organization.findWarehouse(warehouseId)
                .filter(found -> isAdministrator() || organization.isAssigned(requester().userId(), found.id()))
                .orElseThrow(() -> new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "El almacén no existe"));
        if (!warehouse.active()) {
            throw new ConflictException("SITE_INACTIVE", "El almacén está desactivado");
        }
        MaterialSnapshot material = organization.findMaterial(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("MATERIAL_NOT_FOUND", "El material no existe"));
        if (!material.active()) {
            throw new ConflictException("MATERIAL_INACTIVE", "El material está retirado del catálogo");
        }
        return operations.add(warehouseId, materialId, quantity, note);
    }

    @Transactional(readOnly = true)
    public List<StockView> list(UUID warehouseIdFilter) {
        Map<UUID, Optional<WarehouseSnapshot>> warehouses = new HashMap<>();
        Map<UUID, Optional<MaterialSnapshot>> materials = new HashMap<>();
        return stock.findAll().stream()
                .filter(item -> warehouseIdFilter == null || item.locationId().equals(warehouseIdFilter))
                .map(item -> {
                    Optional<WarehouseSnapshot> warehouse = warehouses.computeIfAbsent(item.locationId(), organization::findWarehouse);
                    Optional<MaterialSnapshot> material = materials.computeIfAbsent(item.materialId(), organization::findMaterial);
                    return warehouse.filter(this::canSee).isPresent() && material.isPresent()
                            ? StockView.of(item, warehouse.get(), material.get()) : null;
                })
                .filter(view -> view != null)
                .toList();
    }

    private boolean canSee(WarehouseSnapshot warehouse) {
        return switch (requester().role()) {
            case "ADMINISTRATOR" -> true;
            case "WAREHOUSE_MANAGER" -> organization.isAssigned(requester().userId(), warehouse.id());
            case "SITE_MANAGER" -> warehouse.active();
            default -> false;
        };
    }

    private boolean isAdministrator() {
        return "ADMINISTRATOR".equals(requester().role());
    }

    private static TenantInfo requester() {
        return TenantContext.require();
    }

    public record StockView(UUID warehouseId, String warehouseName, UUID materialId, String sku, String materialName,
            String unit, BigDecimal availableQty, BigDecimal reservedQty) {

        static StockView of(StockItem item, WarehouseSnapshot warehouse, MaterialSnapshot material) {
            return new StockView(warehouse.id(), warehouse.name(), material.id(), material.sku(), material.name(),
                    material.unit(), item.availableQty(), item.reservedQty());
        }
    }
}
