package pe.buildshield.core.organization.domain.model;

/** Tipo de lugar de almacenamiento. */
public enum WarehouseType {

    WAREHOUSE("almacén"),
    COLLECTION_CENTER("centro de acopio");

    private final String displayName;

    WarehouseType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
