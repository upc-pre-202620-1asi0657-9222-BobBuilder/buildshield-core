package pe.buildshield.core.organization.domain.model;

/** Unidad en que se pide, despacha y recibe un material. */
public enum UnitOfMeasure {

    UNIT("unidad"),
    KG("kilogramo"),
    TONNE("tonelada"),
    M("metro"),
    M2("metro cuadrado"),
    M3("metro cúbico"),
    LITER("litro"),
    BAG("bolsa"),
    BOX("caja"),
    ROLL("rollo");

    private final String displayName;

    UnitOfMeasure(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
