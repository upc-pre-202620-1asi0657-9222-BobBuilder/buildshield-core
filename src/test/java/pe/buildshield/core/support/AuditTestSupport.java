package pe.buildshield.core.support;

import pe.buildshield.core.audit.AuditTrail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Las pruebas unitarias conservan sus vistas; la atomicidad se prueba con PostgreSQL real. */
public final class AuditTestSupport {
    private AuditTestSupport() { }
    public static AuditTrail noop() {
        AuditTrail audit = mock(AuditTrail.class);
        lenient().when(audit.recorded(anyString(), anyString(), any())).thenAnswer(call -> call.getArgument(2));
        return audit;
    }
}
