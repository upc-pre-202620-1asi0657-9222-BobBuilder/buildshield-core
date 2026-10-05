package pe.buildshield.core.ordering.domain.model;

import pe.buildshield.commons.error.ErrorDetail;
import pe.buildshield.commons.error.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido de materiales de una obra a un almacén. Las transiciones de estado las decide
 * {@link OrderState}. El identificador lo asigna la persistencia.
 */
public class Order {

    public static final String REJECTION_REASON_REQUIRED = "REJECTION_REASON_REQUIRED";
    static final int MAX_TEXT = 500;

    private final UUID id;
    private final UUID worksiteId;
    private final UUID warehouseId;
    private final UUID requestedBy;
    private final String notes;
    private final Instant placedAt;
    private final List<OrderLine> lines;
    private OrderState state;
    private String rejectionReason;
    private UUID decidedBy;
    private Instant decidedAt;
    private final Long version;

    private Order(UUID id, UUID worksiteId, UUID warehouseId, UUID requestedBy, String notes, Instant placedAt,
            List<OrderLine> lines, OrderState state, String rejectionReason, UUID decidedBy, Instant decidedAt,
            Long version) {
        this.id = id;
        this.worksiteId = Objects.requireNonNull(worksiteId, "worksiteId");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.notes = notes;
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt");
        this.lines = new ArrayList<>(lines);
        this.state = Objects.requireNonNull(state, "state");
        this.rejectionReason = rejectionReason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.version = version;
    }

    /** Crea el pedido en estado Registrado: al menos una línea y sin materiales repetidos. */
    public static Order place(UUID worksiteId, UUID warehouseId, UUID requestedBy, String notes, List<OrderLine> lines,
            Instant now) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("ORDER_WITHOUT_LINES", "El pedido debe tener al menos un material",
                    List.of(new ErrorDetail("lines", "al menos una línea")));
        }
        Set<UUID> materials = new HashSet<>();
        for (OrderLine line : lines) {
            if (!materials.add(line.materialId())) {
                throw new ValidationException("DUPLICATE_MATERIAL",
                        "El material " + line.sku() + " aparece más de una vez en el pedido",
                        List.of(new ErrorDetail("lines", "material repetido: " + line.sku())));
            }
        }
        if (notes != null && notes.length() > MAX_TEXT) {
            throw new ValidationException("INVALID_NOTES", "Las observaciones admiten hasta 500 caracteres");
        }
        return new Order(null, worksiteId, warehouseId, requestedBy, blankToNull(notes), now, lines,
                new OrderState.Registered(), null, null, null, null);
    }

    public static Order restore(UUID id, UUID worksiteId, UUID warehouseId, UUID requestedBy, String notes,
            Instant placedAt, List<OrderLine> lines, OrderStatus status, String rejectionReason, UUID decidedBy,
            Instant decidedAt, Long version) {
        return new Order(Objects.requireNonNull(id, "id"), worksiteId, warehouseId, requestedBy, notes, placedAt, lines,
                OrderState.of(status), rejectionReason, decidedBy, decidedAt, version);
    }

    public void approve(UUID approver, Instant now) {
        state = state.approve();
        decidedBy = approver;
        decidedAt = now;
    }

    /** Rechazo con motivo obligatorio: el pedido pasa a Cancelado. */
    public void reject(UUID approver, String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(REJECTION_REASON_REQUIRED, "El motivo del rechazo es obligatorio",
                    List.of(new ErrorDetail("reason", "obligatorio")));
        }
        if (reason.trim().length() > MAX_TEXT) {
            throw new ValidationException(REJECTION_REASON_REQUIRED, "El motivo admite hasta 500 caracteres",
                    List.of(new ErrorDetail("reason", "máximo 500 caracteres")));
        }
        state = state.reject();
        rejectionReason = reason.trim();
        decidedBy = approver;
        decidedAt = now;
    }

    /** Registra lo despachado de un material (lo usará el módulo de despachos). */
    public void registerDispatch(UUID materialId, BigDecimal quantity) {
        if (!state.acceptsDispatch()) {
            throw new InvalidOrderTransitionException(state.status(), "despachar");
        }
        line(materialId).registerDispatch(quantity);
        state = state.dispatched(lines.stream().allMatch(OrderLine::isComplete));
    }

    /** Cancela el pedido; si ya tenía despachos, cancela lo pendiente y queda Atendido. */
    public void cancel() {
        if (state instanceof OrderState.PartiallyFulfilled) {
            lines.forEach(OrderLine::cancelRemaining);
            state = state.dispatched(true);
            return;
        }
        state = state.cancel();
        lines.forEach(OrderLine::cancelRemaining);
    }

    public void close() {
        state = state.close();
    }

    public OrderLine line(UUID materialId) {
        return lines.stream().filter(line -> line.materialId().equals(materialId)).findFirst()
                .orElseThrow(() -> new ValidationException("MATERIAL_NOT_IN_ORDER", "El material no está en el pedido"));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public UUID id() {
        return id;
    }

    public UUID worksiteId() {
        return worksiteId;
    }

    public UUID warehouseId() {
        return warehouseId;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public String notes() {
        return notes;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public List<OrderLine> lines() {
        return List.copyOf(lines);
    }

    public OrderStatus status() {
        return state.status();
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public UUID decidedBy() {
        return decidedBy;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public Long version() {
        return version;
    }
}
