package pe.buildshield.core.organization.domain.model;

/** Tipo de lugar al que se asigna personal, con el único rol que puede asignarse a él. */
public enum SiteType {

    WORKSITE("obra", "SITE_MANAGER"),
    WAREHOUSE("almacén", "WAREHOUSE_MANAGER");

    private final String displayName;
    private final String assignableRole;

    SiteType(String displayName, String assignableRole) {
        this.displayName = displayName;
        this.assignableRole = assignableRole;
    }

    public String displayName() {
        return displayName;
    }

    /** Encargado de obra → obras; encargado de almacén → almacenes. El administrador ya ve todo. */
    public String assignableRole() {
        return assignableRole;
    }
}
