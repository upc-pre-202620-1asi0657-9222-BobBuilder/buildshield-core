package pe.buildshield.core.inventory.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.buildshield.core.shared.error.ValidationException;
import pe.buildshield.core.inventory.ConcurrentStockModificationException;
import pe.buildshield.core.inventory.InsufficientStockException;
import pe.buildshield.core.inventory.StockService.StockLevel;
import pe.buildshield.core.inventory.domain.model.Quantity;
import pe.buildshield.core.inventory.domain.model.StockItem;
import pe.buildshield.core.inventory.domain.model.StockMovement;
import pe.buildshield.core.inventory.domain.model.StockRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Descuento con bloqueo optimista: hasta 3 reintentos; sin stock, falla sin efectos. */
class StockOperationsTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID CEMENT = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();

    private final StockRepository stock = mock(StockRepository.class);
    private final StockOperations operations = new StockOperations(stock, Clock.fixed(NOW, ZoneOffset.UTC));

    private static StockItem item(String available, long version) {
        return new StockItem(ITEM, WAREHOUSE, CEMENT, new BigDecimal(available), BigDecimal.ZERO, version);
    }

    @Test
    void deducts_and_records_the_movement_with_the_new_balance() {
        when(stock.find(WAREHOUSE, CEMENT)).thenReturn(Optional.of(item("10", 4)));
        when(stock.tryDeduct(ITEM, 4, Quantity.of("7"))).thenReturn(true);

        StockLevel level = operations.deduct(WAREHOUSE, CEMENT, new BigDecimal("7"), "despacho D-1");

        assertThat(level.availableQty()).isEqualByComparingTo("3");
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stock).record(movement.capture());
        assertThat(movement.getValue().type()).isEqualTo(StockMovement.Type.DEDUCTION);
        assertThat(movement.getValue().balanceAfter()).isEqualByComparingTo("3");
        assertThat(movement.getValue().reference()).isEqualTo("despacho D-1");
        assertThat(movement.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void retries_after_a_version_conflict_and_succeeds() {
        when(stock.find(WAREHOUSE, CEMENT)).thenReturn(Optional.of(item("10", 4)), Optional.of(item("9", 5)));
        when(stock.tryDeduct(ITEM, 4, Quantity.of("7"))).thenReturn(false);
        when(stock.tryDeduct(ITEM, 5, Quantity.of("7"))).thenReturn(true);

        StockLevel level = operations.deduct(WAREHOUSE, CEMENT, new BigDecimal("7"), null);

        assertThat(level.availableQty()).isEqualByComparingTo("2");
        verify(stock, times(1)).record(any());
    }

    @Test
    void fails_without_effects_when_a_concurrent_deduction_leaves_too_little() {
        when(stock.find(WAREHOUSE, CEMENT)).thenReturn(Optional.of(item("10", 4)), Optional.of(item("3", 5)));
        when(stock.tryDeduct(ITEM, 4, Quantity.of("7"))).thenReturn(false);

        assertThatThrownBy(() -> operations.deduct(WAREHOUSE, CEMENT, new BigDecimal("7"), null))
                .isInstanceOf(InsufficientStockException.class)
                .hasFieldOrPropertyWithValue("code", InsufficientStockException.CODE)
                .satisfies(ex -> {
                    assertThat(((InsufficientStockException) ex).available()).isEqualByComparingTo("3");
                    assertThat(((InsufficientStockException) ex).requested()).isEqualByComparingTo("7");
                });
        verify(stock, never()).record(any());
    }

    @Test
    void gives_up_after_three_retries() {
        when(stock.find(WAREHOUSE, CEMENT)).thenReturn(Optional.of(item("10", 4)));
        when(stock.tryDeduct(eq(ITEM), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> operations.deduct(WAREHOUSE, CEMENT, new BigDecimal("1"), null))
                .isInstanceOf(ConcurrentStockModificationException.class)
                .hasFieldOrPropertyWithValue("code", "STOCK_CONCURRENT_MODIFICATION");
        verify(stock, times(StockOperations.MAX_RETRIES + 1)).tryDeduct(eq(ITEM), anyLong(), any());
        verify(stock, never()).record(any());
    }

    @Test
    void no_stock_record_means_insufficient_stock() {
        when(stock.find(WAREHOUSE, CEMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operations.deduct(WAREHOUSE, CEMENT, new BigDecimal("1"), null))
                .isInstanceOf(InsufficientStockException.class);
        verify(stock, never()).tryDeduct(any(), anyLong(), any());
    }

    @Test
    void more_than_available_fails_before_trying_to_update() {
        when(stock.find(WAREHOUSE, CEMENT)).thenReturn(Optional.of(item("5", 1)));

        assertThatThrownBy(() -> operations.deduct(WAREHOUSE, CEMENT, new BigDecimal("5.001"), null))
                .isInstanceOf(InsufficientStockException.class);
        verify(stock, never()).tryDeduct(any(), anyLong(), any());
    }

    @Test
    void invalid_quantity_is_rejected() {
        assertThatThrownBy(() -> operations.deduct(WAREHOUSE, CEMENT, BigDecimal.ZERO, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> operations.add(WAREHOUSE, CEMENT, new BigDecimal("-3"), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void entry_adds_and_records_the_movement() {
        when(stock.add(WAREHOUSE, CEMENT, Quantity.of("100"))).thenReturn(item("120", 6));

        StockLevel level = operations.add(WAREHOUSE, CEMENT, new BigDecimal("100"), "Guía 001");

        assertThat(level.availableQty()).isEqualByComparingTo("120");
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stock).record(movement.capture());
        assertThat(movement.getValue().type()).isEqualTo(StockMovement.Type.ENTRY);
        assertThat(movement.getValue().quantity()).isEqualByComparingTo("100");
        assertThat(movement.getValue().balanceAfter()).isEqualByComparingTo("120");
    }
}
