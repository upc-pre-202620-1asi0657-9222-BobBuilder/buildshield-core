package pe.buildshield.core.ordering.domain.model;

/** Estados de un pedido, con su nombre visible en español. */
public enum OrderStatus {

    REGISTERED("Registrado"),
    IN_REVIEW("EnRevision"),
    PARTIALLY_FULFILLED("ParcialmenteAtendido"),
    FULFILLED("Atendido"),
    CLOSED("Cerrado"),
    CANCELLED("Cancelado");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
