package pe.buildshield.core.reception.domain.model;

/** Estados de una recepción en obra, con su nombre visible en español. */
public enum ReceptionStatus {

    IN_PROGRESS("EnCurso"),
    CONFIRMED("Confirmada");

    private final String label;

    ReceptionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
