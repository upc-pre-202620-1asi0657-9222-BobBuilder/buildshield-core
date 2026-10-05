package pe.buildshield.core.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.buildshield.commons.tenant.TenantContext;
import pe.buildshield.commons.tenant.TenantInfo;
import pe.buildshield.core.support.CoreIntegrationTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QAS02: dos operaciones simultáneas descuentan el mismo material y el stock no alcanza para ambas.
 * Solo una gana, la otra recibe {@link InsufficientStockException} sin efectos, y el inventario nunca
 * queda negativo. Se repite 50 veces para que el entrelazado de los hilos varíe.
 */
@CoreIntegrationTest
@Testcontainers(disabledWithoutDocker = true)
class StockConcurrencyIntegrationTest {

    private static final TenantInfo WAREHOUSE_MANAGER =
            new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "WAREHOUSE_MANAGER");
    private static final BigDecimal INITIAL = new BigDecimal("10");
    private static final BigDecimal EACH_DEDUCTION = new BigDecimal("7");

    @Autowired
    StockService stockService;

    @Autowired
    JdbcTemplate jdbc;

    private UUID warehouse;
    private UUID cement;

    @BeforeEach
    void stockOfTen() {
        warehouse = UUID.randomUUID();
        cement = UUID.randomUUID();
        TenantContext.runAs(WAREHOUSE_MANAGER, () -> stockService.add(warehouse, cement, INITIAL));
    }

    @RepeatedTest(value = 50, name = "repetición {currentRepetition} de {totalRepetitions}")
    void only_one_of_two_simultaneous_deductions_wins_and_stock_never_goes_negative(RepetitionInfo repetition)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Object> deduct = () -> TenantContext.callAs(WAREHOUSE_MANAGER, () -> {
            ready.countDown();
            await(go);
            try {
                return stockService.deduct(warehouse, cement, EACH_DEDUCTION, "despacho");
            } catch (InsufficientStockException | ConcurrentStockModificationException rejected) {
                return rejected;
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(deduct);
            Future<Object> second = pool.submit(deduct);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(StockService.StockLevel.class::isInstance)
                    .as("repetición %d: exactamente un descuento gana", repetition.getCurrentRepetition()).hasSize(1);
            assertThat(outcomes).filteredOn(InsufficientStockException.class::isInstance)
                    .as("el otro se rechaza por stock insuficiente").hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        BigDecimal available = jdbc.queryForObject("""
                SELECT available_qty FROM inventory.stock_items
                WHERE organization_id = ? AND location_id = ? AND material_id = ?""",
                BigDecimal.class, WAREHOUSE_MANAGER.organizationId(), warehouse, cement);
        assertThat(available).isEqualByComparingTo("3").isNotNegative();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM inventory.stock_movements m JOIN inventory.stock_items i ON i.id = m.stock_item_id
                WHERE i.location_id = ? AND m.type = 'DEDUCTION'""", Long.class, warehouse))
                .as("el perdedor no deja movimiento").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory.stock_items WHERE available_qty < 0 OR reserved_qty < 0", Long.class))
                .as("ningún saldo negativo en toda la tabla").isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
