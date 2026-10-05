package pe.buildshield.core.shared.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import pe.buildshield.core.shared.tenant.TenantContext;

import java.time.Clock;

/** Borra las claves de idempotencia con más de 24 horas. */
public class IdempotencyKeyPurger {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurger.class);

    private final IdempotencyStore store;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public IdempotencyKeyPurger(IdempotencyStore store, TransactionTemplate transactionTemplate, Clock clock) {
        this.store = store;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${buildshield.idempotency.purge-interval:PT1H}")
    public int purge() {
        Integer deleted = TenantContext.callAsSystem(() -> transactionTemplate.execute(status ->
                store.deleteCreatedBefore(clock.instant().minus(IdempotencyKeyFilter.RETENTION))));
        if (deleted != null && deleted > 0) {
            log.info("Se borraron {} claves de idempotencia vencidas", deleted);
        }
        return deleted == null ? 0 : deleted;
    }
}
