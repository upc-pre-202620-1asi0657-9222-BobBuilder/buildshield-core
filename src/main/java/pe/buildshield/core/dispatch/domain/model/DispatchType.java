package pe.buildshield.core.dispatch.domain.model;

/**
 * Tipo de despacho (US23): {@code COMPLETE} si con él ya no queda nada pendiente del pedido;
 * {@code PARTIAL} si el pedido se atiende en varios despachos y todavía queda algo pendiente.
 */
public enum DispatchType {

    COMPLETE("Completo"),
    PARTIAL("Parcial");

    private final String label;

    DispatchType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
