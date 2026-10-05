package pe.buildshield.core.organization.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Material del catálogo de la organización. El SKU lo identifica y no cambia; nombre, unidad y
 * tolerancia de merma sí. No se borra: se desactiva.
 */
public class Material {

    private final UUID id;
    private final Sku sku;
    private String name;
    private UnitOfMeasure unit;
    private WasteTolerance wasteTolerance;
    private boolean active;
    private final Long version;

    private Material(UUID id, Sku sku, String name, UnitOfMeasure unit, WasteTolerance wasteTolerance, boolean active,
            Long version) {
        this.id = id;
        this.sku = Objects.requireNonNull(sku, "sku");
        this.name = name;
        this.unit = unit;
        this.wasteTolerance = Objects.requireNonNull(wasteTolerance, "wasteTolerance");
        this.active = active;
        this.version = version;
    }

    public static Material register(Sku sku, String name, UnitOfMeasure unit, WasteTolerance wasteTolerance) {
        return new Material(null, sku, Names.require(name, "name"), requireUnit(unit), wasteTolerance, true, null);
    }

    public static Material restore(UUID id, Sku sku, String name, UnitOfMeasure unit, WasteTolerance wasteTolerance,
            boolean active, Long version) {
        return new Material(Objects.requireNonNull(id, "id"), sku, name, unit, wasteTolerance, active, version);
    }

    /** Cambio parcial: los argumentos nulos no cambian. El SKU no se modifica. */
    public void update(String newName, UnitOfMeasure newUnit, WasteTolerance newTolerance, Boolean newActive) {
        if (newName != null) {
            name = Names.require(newName, "name");
        }
        if (newUnit != null) {
            unit = newUnit;
        }
        if (newTolerance != null) {
            wasteTolerance = newTolerance;
        }
        if (newActive != null) {
            active = newActive;
        }
    }

    private static UnitOfMeasure requireUnit(UnitOfMeasure unit) {
        if (unit == null) {
            throw new ValidationException("INVALID_UNIT", "La unidad es obligatoria",
                    List.of(new ErrorDetail("unit", "obligatoria")));
        }
        return unit;
    }

    public UUID id() {
        return id;
    }

    public Sku sku() {
        return sku;
    }

    public String name() {
        return name;
    }

    public UnitOfMeasure unit() {
        return unit;
    }

    public WasteTolerance wasteTolerance() {
        return wasteTolerance;
    }

    public boolean active() {
        return active;
    }

    public Long version() {
        return version;
    }
}
