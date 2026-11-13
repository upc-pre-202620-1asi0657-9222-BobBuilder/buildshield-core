package pe.buildshield.core.dispatch;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.core.dispatch.application.DispatchReceipts;
import pe.buildshield.core.dispatch.domain.model.Dispatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fachada pública del módulo dispatch para el módulo reception. Devuelve copias inmutables y se une a
 * la transacción de quien llama: confirmar la recepción y marcar el despacho Recibido van juntos.
 *
 * <p>Trabaja dentro de la organización del {@code TenantContext}; no aplica la visibilidad por rol.
 */
@Component
public class DispatchFacade {

    private final DispatchReceipts receipts;

    public DispatchFacade(DispatchReceipts receipts) {
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public Optional<DispatchSnapshot> findDispatch(UUID dispatchId) {
        return receipts.find(dispatchId).map(DispatchSnapshot::of);
    }

    /**
     * Marca el despacho Recibido (EnTransito → Recibido).
     *
     * @throws pe.buildshield.core.shared.error.ConflictException {@code INVALID_DISPATCH_TRANSITION} si no está
     *         en tránsito
     */
    @Transactional
    public DispatchSnapshot markReceived(UUID dispatchId) {
        return DispatchSnapshot.of(receipts.markReceived(dispatchId));
    }

    /** @param status PREPARED, IN_TRANSIT, RECEIVED o CANCELLED */
    public record DispatchSnapshot(UUID id, UUID orderId, UUID warehouseId, UUID worksiteId, String status,
            String manifestCode, List<DispatchLineSnapshot> lines) {

        public static final String IN_TRANSIT = "IN_TRANSIT";

        static DispatchSnapshot of(Dispatch dispatch) {
            return new DispatchSnapshot(dispatch.id(), dispatch.orderId(), dispatch.warehouseId(), dispatch.worksiteId(),
                    dispatch.status().name(), dispatch.manifestCode().value(), dispatch.lines().stream()
                    .map(line -> new DispatchLineSnapshot(line.id(), line.orderLineId(), line.materialId(), line.quantity()))
                    .toList());
        }

        public boolean inTransit() {
            return IN_TRANSIT.equals(status);
        }
    }

    public record DispatchLineSnapshot(UUID id, UUID orderLineId, UUID materialId, BigDecimal quantity) {
    }
}
