package pe.buildshield.core.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.commons.error.ResourceNotFoundException;
import pe.buildshield.core.organization.domain.model.Warehouse;
import pe.buildshield.core.organization.domain.model.WarehouseRepository;
import pe.buildshield.core.organization.domain.model.WarehouseType;

import java.util.List;
import java.util.UUID;

/** US15: almacenes y centros de acopio; se desactivan y reactivan, nunca se borran. */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final SiteVisibility visibility;

    public WarehouseService(WarehouseRepository warehouses, SiteVisibility visibility) {
        this.warehouses = warehouses;
        this.visibility = visibility;
    }

    @Transactional
    public WarehouseView register(RegisterWarehouse command) {
        return WarehouseView.of(warehouses.save(Warehouse.register(command.name(), command.type(), command.address())));
    }

    @Transactional(readOnly = true)
    public WarehouseView get(UUID id) {
        return WarehouseView.of(warehouses.findById(id).filter(visibility::canSee).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public List<WarehouseView> list() {
        return visibility.visibleWarehouses().stream().map(WarehouseView::of).toList();
    }

    @Transactional
    public WarehouseView update(UUID id, UpdateWarehouse command) {
        Warehouse warehouse = warehouses.findById(id).orElseThrow(() -> notFound(id));
        warehouse.update(command.name(), command.type(), command.address());
        if (Boolean.FALSE.equals(command.active())) {
            warehouse.deactivate();
        } else if (Boolean.TRUE.equals(command.active())) {
            warehouse.activate();
        }
        return WarehouseView.of(warehouses.save(warehouse));
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("WAREHOUSE_NOT_FOUND", "El almacén " + id + " no existe");
    }

    public record RegisterWarehouse(String name, WarehouseType type, String address) {
    }

    /** Campos nulos: no cambian. {@code active=false} desactiva, {@code true} reactiva. */
    public record UpdateWarehouse(String name, WarehouseType type, String address, Boolean active) {
    }

    public record WarehouseView(UUID id, String name, WarehouseType type, String address, boolean active) {

        static WarehouseView of(Warehouse warehouse) {
            return new WarehouseView(warehouse.id(), warehouse.name(), warehouse.type(), warehouse.address(),
                    warehouse.active());
        }
    }
}
