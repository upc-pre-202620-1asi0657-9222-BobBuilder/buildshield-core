package pe.buildshield.core.reception.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceptionTest {

    private static final Instant NOW = Instant.parse("2026-11-03T16:30:00Z");
    private static final UUID JORGE = UUID.randomUUID();
    private static final UUID CEMENT_LINE = UUID.randomUUID();
    private static final UUID STEEL_LINE = UUID.randomUUID();

    private static Reception inProgress() {
        return Reception.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(
                        ReceptionLine.restore(CEMENT_LINE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                new BigDecimal("50"), null, null),
                        ReceptionLine.restore(STEEL_LINE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                new BigDecimal("120"), null, null)),
                ReceptionStatus.IN_PROGRESS, null, null, 0L);
    }

    @Test
    void starts_in_progress_with_one_line_per_dispatch_line() {
        Reception reception = Reception.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(ReceptionLine.expect(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN)));

        assertThat(reception.id()).isNull();
        assertThat(reception.status()).isEqualTo(ReceptionStatus.IN_PROGRESS);
        assertThat(reception.lines()).singleElement().satisfies(line -> {
            assertThat(line.isRecorded()).isFalse();
            assertThat(line.difference()).isNull();
            assertThat(line.dispatchedQty()).isEqualByComparingTo("10");
        });
        assertThatThrownBy(() -> Reception.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void records_received_progressively_and_computes_the_shrinkage() {
        Reception reception = inProgress();

        reception.recordReceived(CEMENT_LINE, new BigDecimal("49"));
        assertThat(reception.line(CEMENT_LINE).shrinkagePercent()).isEqualByComparingTo("2.00");
        assertThat(reception.line(CEMENT_LINE).difference()).isEqualByComparingTo("1");

        reception.recordReceived(CEMENT_LINE, new BigDecimal("50"));
        assertThat(reception.line(CEMENT_LINE).shrinkagePercent()).isEqualByComparingTo("0");
        reception.recordReceived(STEEL_LINE, new BigDecimal("110"));
        assertThat(reception.line(STEEL_LINE).shrinkagePercent()).isEqualByComparingTo("8.33");
    }

    @ParameterizedTest
    @CsvSource({"-1, INVALID_QUANTITY", "0.0001, INVALID_QUANTITY", "50.001, RECEIVED_EXCEEDS_DISPATCHED"})
    void received_is_zero_or_more_and_never_exceeds_dispatched(String received, String code) {
        assertThatThrownBy(() -> inProgress().recordReceived(CEMENT_LINE, new BigDecimal(received)))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", code);
        assertThatThrownBy(() -> inProgress().recordReceived(CEMENT_LINE, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void unknown_line_is_404() {
        assertThatThrownBy(() -> inProgress().recordReceived(UUID.randomUUID(), BigDecimal.ONE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", Reception.RECEPTION_LINE_NOT_FOUND);
    }

    @Test
    void confirmation_requires_every_line_and_happens_once() {
        Reception reception = inProgress();
        reception.recordReceived(CEMENT_LINE, new BigDecimal("49"));
        assertThatThrownBy(() -> reception.confirm(JORGE, NOW)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Reception.RECEPTION_INCOMPLETE);

        reception.recordReceived(STEEL_LINE, BigDecimal.ZERO);
        reception.confirm(JORGE, NOW);

        assertThat(reception.status()).isEqualTo(ReceptionStatus.CONFIRMED);
        assertThat(reception.confirmedBy()).isEqualTo(JORGE);
        assertThat(reception.confirmedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> reception.confirm(JORGE, NOW)).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Reception.RECEPTION_ALREADY_CONFIRMED);
        assertThatThrownBy(() -> reception.recordReceived(CEMENT_LINE, BigDecimal.ONE))
                .isInstanceOf(ConflictException.class);
        assertThat(ReceptionStatus.CONFIRMED.label()).isEqualTo("Confirmada");
    }

    @Test
    void shrinkage_is_compared_with_the_tolerance() {
        assertThat(Shrinkage.percent(new BigDecimal("120"), new BigDecimal("110"))).isEqualByComparingTo("8.33");
        assertThat(Shrinkage.withinTolerance(new BigDecimal("2.50"), new BigDecimal("2.5"))).isTrue();
        assertThat(Shrinkage.withinTolerance(new BigDecimal("2.51"), new BigDecimal("2.5"))).isFalse();
        assertThatThrownBy(() -> Shrinkage.percent(BigDecimal.ZERO, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceptionLine.expect(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
