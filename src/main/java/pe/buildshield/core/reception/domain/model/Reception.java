package pe.buildshield.core.reception.domain.model;

import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Recepción en obra de un despacho en tránsito (US35, US37). Hay una sola por despacho. Lo recibido de
 * cada línea se registra de a poco (EnCurso) y la conformidad la cierra (Confirmada): después ya no
 * cambia (QAS10).
 */
public class Reception {

    public static final String RECEPTION_ALREADY_CONFIRMED = "RECEPTION_ALREADY_CONFIRMED";
    public static final String RECEPTION_INCOMPLETE = "RECEPTION_INCOMPLETE";
    public static final String RECEPTION_LINE_NOT_FOUND = "RECEPTION_LINE_NOT_FOUND";

    private final UUID id;
    private final UUID dispatchId;
    private final UUID orderId;
    private final UUID warehouseId;
    private final UUID worksiteId;
    private final List<ReceptionLine> lines;
    private ReceptionStatus status;
    private UUID confirmedBy;
    private Instant confirmedAt;
    private final Long version;

    private Reception(UUID id, UUID dispatchId, UUID orderId, UUID warehouseId, UUID worksiteId,
            List<ReceptionLine> lines, ReceptionStatus status, UUID confirmedBy, Instant confirmedAt, Long version) {
        this.id = id;
        this.dispatchId = Objects.requireNonNull(dispatchId, "dispatchId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.worksiteId = Objects.requireNonNull(worksiteId, "worksiteId");
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("Una recepción tiene al menos una línea");
        }
        this.lines = new ArrayList<>(lines);
        this.status = Objects.requireNonNull(status, "status");
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        this.version = version;
    }

    /** Abre la recepción con una línea por cada línea del despacho, sin nada recibido todavía. */
    public static Reception start(UUID dispatchId, UUID orderId, UUID warehouseId, UUID worksiteId,
            List<ReceptionLine> lines) {
        return new Reception(null, dispatchId, orderId, warehouseId, worksiteId, lines, ReceptionStatus.IN_PROGRESS,
                null, null, null);
    }

    public static Reception restore(UUID id, UUID dispatchId, UUID orderId, UUID warehouseId, UUID worksiteId,
            List<ReceptionLine> lines, ReceptionStatus status, UUID confirmedBy, Instant confirmedAt, Long version) {
        return new Reception(Objects.requireNonNull(id, "id"), dispatchId, orderId, warehouseId, worksiteId, lines,
                status, confirmedBy, confirmedAt, version);
    }

    /** US35: registra lo recibido de una línea mientras la recepción está en curso. */
    public void recordReceived(UUID lineId, BigDecimal quantity) {
        requireInProgress();
        line(lineId).recordReceived(quantity);
    }

    /** US37: conformidad. Todas las líneas deben tener lo recibido; después la recepción no cambia. */
    public void confirm(UUID by, Instant now) {
        requireInProgress();
        List<String> missing = lines.stream().filter(line -> !line.isRecorded())
                .map(line -> String.valueOf(line.id())).toList();
        if (!missing.isEmpty()) {
            throw new ConflictException(RECEPTION_INCOMPLETE, "Falta registrar lo recibido de " + missing.size()
                    + " línea(s)", missing.stream().map(line -> new ErrorDetail("lineId", line)).toList());
        }
        status = ReceptionStatus.CONFIRMED;
        confirmedBy = Objects.requireNonNull(by, "by");
        confirmedAt = Objects.requireNonNull(now, "now");
    }

    public ReceptionLine line(UUID lineId) {
        return lines.stream().filter(line -> line.id() != null && line.id().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(RECEPTION_LINE_NOT_FOUND,
                        "La línea " + lineId + " no es de la recepción"));
    }

    private void requireInProgress() {
        if (status != ReceptionStatus.IN_PROGRESS) {
            throw new ConflictException(RECEPTION_ALREADY_CONFIRMED, "La recepción ya fue confirmada y no cambia");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID dispatchId() {
        return dispatchId;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID warehouseId() {
        return warehouseId;
    }

    public UUID worksiteId() {
        return worksiteId;
    }

    public List<ReceptionLine> lines() {
        return List.copyOf(lines);
    }

    public ReceptionStatus status() {
        return status;
    }

    public UUID confirmedBy() {
        return confirmedBy;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Long version() {
        return version;
    }
}
