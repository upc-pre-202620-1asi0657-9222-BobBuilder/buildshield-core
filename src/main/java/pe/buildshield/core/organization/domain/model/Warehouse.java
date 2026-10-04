package pe.buildshield.core.organization.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Almacén o centro de acopio desde el que se despachan materiales. No se borra: se desactiva y
 * conserva su historial (asignaciones, despachos).
 */
public class Warehouse {

    static final int MAX_ADDRESS = 200;

    private final UUID id;
    private String name;
    private WarehouseType type;
    private String address;
    private boolean active;
    private final Long version;

    private Warehouse(UUID id, String name, WarehouseType type, String address, boolean active, Long version) {
        this.id = id;
        this.name = name;
        this.type = Objects.requireNonNull(type, "type");
        this.address = address;
        this.active = active;
        this.version = version;
    }

    public static Warehouse register(String name, WarehouseType type, String address) {
        if (type == null) {
            throw new ValidationException("INVALID_WAREHOUSE_TYPE", "El tipo es obligatorio",
                    List.of(new ErrorDetail("type", "WAREHOUSE o COLLECTION_CENTER")));
        }
        return new Warehouse(null, Names.require(name, "name"), type, requireAddress(address), true, null);
    }

    public static Warehouse restore(UUID id, String name, WarehouseType type, String address, boolean active,
            Long version) {
        return new Warehouse(Objects.requireNonNull(id, "id"), name, type, address, active, version);
    }

    /** Cambio parcial: los argumentos nulos no cambian. */
    public void update(String newName, WarehouseType newType, String newAddress) {
        String name = newName != null ? Names.require(newName, "name") : this.name;
        String address = newAddress != null ? requireAddress(newAddress) : this.address;
        this.name = name;
        this.address = address;
        if (newType != null) {
            this.type = newType;
        }
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    private static String requireAddress(String address) {
        if (address == null || address.isBlank() || address.trim().length() > MAX_ADDRESS) {
            throw new ValidationException("INVALID_ADDRESS", "La dirección es obligatoria (máximo 200 caracteres)",
                    List.of(new ErrorDetail("address", "obligatoria, máximo 200 caracteres")));
        }
        return address.trim();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public WarehouseType type() {
        return type;
    }

    public String address() {
        return address;
    }

    public boolean active() {
        return active;
    }

    public Long version() {
        return version;
    }
}
