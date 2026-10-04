package pe.buildshield.core.organization.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Ubicación de una obra: dirección, distrito y ciudad obligatorios; coordenadas opcionales (ambas o
 * ninguna).
 */
public record Location(String address, String district, String city, Double latitude, Double longitude) {

    static final int MAX_ADDRESS = 200;
    static final int MAX_PLACE = 100;

    public Location {
        List<ErrorDetail> errors = new ArrayList<>();
        address = required(address, "address", MAX_ADDRESS, errors);
        district = required(district, "district", MAX_PLACE, errors);
        city = required(city, "city", MAX_PLACE, errors);
        if ((latitude == null) != (longitude == null)) {
            errors.add(new ErrorDetail("latitude", "latitud y longitud van juntas"));
        } else if (latitude != null) {
            if (latitude < -90 || latitude > 90) {
                errors.add(new ErrorDetail("latitude", "debe estar entre -90 y 90"));
            }
            if (longitude < -180 || longitude > 180) {
                errors.add(new ErrorDetail("longitude", "debe estar entre -180 y 180"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("INVALID_LOCATION", "La ubicación de la obra no es válida", errors);
        }
    }

    private static String required(String value, String field, int max, List<ErrorDetail> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new ErrorDetail(field, "es obligatorio"));
            return value;
        }
        if (value.trim().length() > max) {
            errors.add(new ErrorDetail(field, "máximo " + max + " caracteres"));
        }
        return value.trim();
    }
}
