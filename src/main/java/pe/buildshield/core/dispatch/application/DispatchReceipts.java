package pe.buildshield.core.dispatch.application;

import org.springframework.stereotype.Service;
import pe.buildshield.core.dispatch.domain.model.Dispatch;
import pe.buildshield.core.dispatch.domain.model.DispatchRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el módulo reception necesita de un despacho: leerlo y marcarlo Recibido al confirmar la
 * recepción. Sin visibilidad por rol (la decide reception), dentro de la organización del contexto.
 */
@Service
public class DispatchReceipts {

    private final DispatchRepository dispatches;
    private final Clock clock;

    public DispatchReceipts(DispatchRepository dispatches, Clock clock) {
        this.dispatches = dispatches;
        this.clock = clock;
    }

    public Optional<Dispatch> find(UUID dispatchId) {
        return dispatches.findById(dispatchId);
    }

    /** EnTransito → Recibido; en otro estado responde 409 {@code INVALID_DISPATCH_TRANSITION}. */
    public Dispatch markReceived(UUID dispatchId) {
        Dispatch dispatch = dispatches.findById(dispatchId).orElseThrow(() -> DispatchService.notFound(dispatchId));
        dispatch.markReceived(clock.instant());
        return dispatches.save(dispatch);
    }
}
