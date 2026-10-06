package pe.buildshield.core.dispatch.domain.model;

/** Estados de un despacho, con su nombre visible en español. */
public enum DispatchStatus {

    PREPARED("Preparado"),
    IN_TRANSIT("EnTransito"),
    RECEIVED("Recibido"),
    CANCELLED("Anulado");

    private final String label;

    DispatchStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
