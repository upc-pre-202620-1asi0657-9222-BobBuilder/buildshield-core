package pe.buildshield.core.organization.domain.model;

import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Obra: lugar al que se piden y despachan materiales. La fecha de fin es opcional (obra abierta) y
 * nunca puede ser anterior a la de inicio. El identificador lo asigna la persistencia.
 */
public class Worksite {

    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";

    private final UUID id;
    private String name;
    private Location location;
    private LocalDate startDate;
    private LocalDate endDate;
    private final Long version;

    private Worksite(UUID id, String name, Location location, LocalDate startDate, LocalDate endDate, Long version) {
        this.id = id;
        this.name = name;
        this.location = Objects.requireNonNull(location, "location");
        this.startDate = startDate;
        this.endDate = endDate;
        this.version = version;
    }

    public static Worksite register(String name, Location location, LocalDate startDate, LocalDate endDate) {
        requireStart(startDate);
        checkRange(startDate, endDate);
        return new Worksite(null, Names.require(name, "name"), location, startDate, endDate, null);
    }

    public static Worksite restore(UUID id, String name, Location location, LocalDate startDate, LocalDate endDate,
            Long version) {
        return new Worksite(Objects.requireNonNull(id, "id"), name, location, startDate, endDate, version);
    }

    /** Cambio parcial: los argumentos nulos no cambian. La regla de fechas se aplica al resultado. */
    public void update(String newName, Location newLocation, LocalDate newStartDate, LocalDate newEndDate) {
        LocalDate start = newStartDate != null ? newStartDate : startDate;
        LocalDate end = newEndDate != null ? newEndDate : endDate;
        checkRange(start, end);
        if (newName != null) {
            name = Names.require(newName, "name");
        }
        if (newLocation != null) {
            location = newLocation;
        }
        startDate = start;
        endDate = end;
    }

    private static void requireStart(LocalDate startDate) {
        if (startDate == null) {
            throw new ValidationException(INVALID_DATE_RANGE, "La fecha de inicio es obligatoria",
                    List.of(new ErrorDetail("startDate", "es obligatoria")));
        }
    }

    private static void checkRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new ValidationException(INVALID_DATE_RANGE,
                    "La fecha de fin no puede ser anterior a la fecha de inicio",
                    List.of(new ErrorDetail("endDate", "anterior a la fecha de inicio " + startDate)));
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Location location() {
        return location;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public Long version() {
        return version;
    }
}
