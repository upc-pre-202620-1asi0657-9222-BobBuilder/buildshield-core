package pe.buildshield.core.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.buildshield.core.inventory.application.StockOperations;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QAS02 con reservas (RF40): dos aprobaciones simultáneas reservan el mismo material y el stock no
 * alcanza para ambas. Solo una reserva gana; la otra falla sin efectos y el disponible nunca queda
 * negativo. Además, el consumo al despachar (RF41) nunca supera lo reservado.
 */
@CoreIntegrationTest
class StockReservationConcurrencyIntegrationTest {

    private static final TenantInfo WAREHOUSE_MANAGER =
            new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), "WAREHOUSE_MANAGER");

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
        TenantContext.runAs(WAREHOUSE_MANAGER, () -> stockService.add(warehouse, cement, new BigDecimal("10")));
    }

    @RepeatedTest(value = 20, name = "repetición {currentRepetition} de {totalRepetitions}")
    void only_one_of_two_simultaneous_reservations_wins(RepetitionInfo repetition) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Object> reserve = () -> TenantContext.callAs(WAREHOUSE_MANAGER, () -> {
            ready.countDown();
            try {
                go.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            try {
                return stockService.reserve(warehouse, cement, new BigDecimal("7"), UUID.randomUUID(), UUID.randomUUID());
            } catch (InsufficientStockException | ConcurrentStockModificationException rejected) {
                return rejected;
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(reserve);
            Future<Object> second = pool.submit(reserve);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(StockService.StockLevel.class::isInstance)
                    .as("repetición %d: exactamente una reserva gana", repetition.getCurrentRepetition()).hasSize(1);
            assertThat(outcomes).filteredOn(InsufficientStockException.class::isInstance).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(level("available_qty")).isEqualByComparingTo("3");
        assertThat(level("reserved_qty")).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM inventory.stock_reservations r JOIN inventory.stock_items i ON i.id = r.stock_item_id
                WHERE i.location_id = ?""", Long.class, warehouse)).as("el perdedor no deja reserva").isEqualTo(1);
    }

    @Test
    void consumption_never_exceeds_what_was_reserved() {
        UUID line = UUID.randomUUID();
        TenantContext.runAs(WAREHOUSE_MANAGER, () -> {
            stockService.reserve(warehouse, cement, new BigDecimal("6"), UUID.randomUUID(), line);
            stockService.consumeReservation(warehouse, cement, new BigDecimal("4"), line, "Despacho 1");
            assertThatThrownBy(() -> stockService.consumeReservation(warehouse, cement, new BigDecimal("3"), line, "Despacho 2"))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code", StockOperations.RESERVATION_NOT_AVAILABLE);
            stockService.consumeReservation(warehouse, cement, new BigDecimal("2"), line, "Despacho 2");
        });

        assertThat(level("available_qty")).isEqualByComparingTo("4");
        assertThat(level("reserved_qty")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory.stock_reservations WHERE order_line_id = ?",
                String.class, line)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM inventory.stock_movements m JOIN inventory.stock_items i ON i.id = m.stock_item_id
                WHERE i.location_id = ? AND m.type = 'DISPATCH'""", Long.class, warehouse)).isEqualTo(2);
    }

    private BigDecimal level(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM inventory.stock_items WHERE organization_id = ? "
                + "AND location_id = ? AND material_id = ?", BigDecimal.class, WAREHOUSE_MANAGER.organizationId(),
                warehouse, cement);
    }
}
