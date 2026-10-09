package pe.buildshield.core.dispatch.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import pe.buildshield.core.shared.error.ConflictException;
import pe.buildshield.core.shared.error.ValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchTest {

    private static final Instant NOW = Instant.parse("2026-11-03T13:00:00Z");
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID WORKSITE = UUID.randomUUID();
    private static final UUID ROSA = UUID.randomUUID();
    private static final ManifestCode CODE = new ManifestCode("MAN-20261103-7KQ2M9XA");

    private static DispatchLine line(String quantity) {
        return DispatchLine.of(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(quantity));
    }

    private static Dispatch prepared() {
        return Dispatch.prepare(ORDER, WAREHOUSE, WORKSITE, DispatchType.PARTIAL, CODE, List.of(line("30")), NOW);
    }

    private static Carrier carrier() {
        return new Carrier(" Transportes Rímac SAC ", "20555666777", "abc-123");
    }

    private static DepartureWeighing weighing() {
        return DepartureWeighing.of(new BigDecimal("2550.5"), new BigDecimal("1050.5"), null, NOW, ROSA);
    }

    @Test
    void prepared_dispatch_keeps_its_order_sites_lines_and_manifest() {
        Dispatch dispatch = prepared();

        assertThat(dispatch.id()).isNull();
        assertThat(dispatch.status()).isEqualTo(DispatchStatus.PREPARED);
        assertThat(dispatch.orderId()).isEqualTo(ORDER);
        assertThat(dispatch.warehouseId()).isEqualTo(WAREHOUSE);
        assertThat(dispatch.worksiteId()).isEqualTo(WORKSITE);
        assertThat(dispatch.type()).isEqualTo(DispatchType.PARTIAL);
        assertThat(dispatch.manifestCode()).isEqualTo(CODE);
        assertThat(dispatch.preparedAt()).isEqualTo(NOW);
        assertThat(dispatch.lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualByComparingTo("30"));
        assertThat(dispatch.carrier()).isEmpty();
        assertThat(dispatch.departureWeighing()).isEmpty();
        assertThat(dispatch.version()).isNull();
    }

    @Test
    void needs_lines_without_repeating_an_order_line() {
        assertThatThrownBy(() -> Dispatch.prepare(ORDER, WAREHOUSE, WORKSITE, DispatchType.PARTIAL, CODE, List.of(), NOW))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "DISPATCH_WITHOUT_LINES");
        UUID orderLine = UUID.randomUUID();
        List<DispatchLine> repeated = List.of(DispatchLine.of(orderLine, UUID.randomUUID(), BigDecimal.ONE),
                DispatchLine.of(orderLine, UUID.randomUUID(), BigDecimal.TEN));
        assertThatThrownBy(() -> Dispatch.requireLines(repeated))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "DUPLICATE_ORDER_LINE");
        assertThatThrownBy(() -> Dispatch.requireLines(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void departs_with_carrier_and_weighing_and_then_is_received() {
        Dispatch dispatch = prepared();
        dispatch.assignCarrier(carrier());
        dispatch.assignCarrier(new Carrier("Transportes Rímac SAC", "20555666777", "ABC123"));
        dispatch.recordDepartureWeighing(weighing());

        dispatch.depart(NOW.plusSeconds(600));

        assertThat(dispatch.status()).isEqualTo(DispatchStatus.IN_TRANSIT);
        assertThat(dispatch.departedAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(dispatch.carrier()).get().extracting(Carrier::plate).isEqualTo("ABC123");
        assertThat(dispatch.departureWeighing()).get().extracting(DepartureWeighing::netKg)
                .satisfies(net -> assertThat(net).isEqualByComparingTo("1500"));

        dispatch.markReceived(NOW.plusSeconds(3600));
        assertThat(dispatch.status()).isEqualTo(DispatchStatus.RECEIVED);
        assertThat(dispatch.receivedAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void cannot_depart_without_carrier_or_weighing() {
        Dispatch dispatch = prepared();
        assertThatThrownBy(() -> dispatch.depart(NOW))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", Dispatch.CARRIER_REQUIRED);
        dispatch.assignCarrier(carrier());
        assertThatThrownBy(() -> dispatch.depart(NOW))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Dispatch.DEPARTURE_WEIGHING_REQUIRED);
        assertThat(dispatch.status()).isEqualTo(DispatchStatus.PREPARED);
    }

    @Test
    void only_one_departure_weighing() {
        Dispatch dispatch = prepared();
        dispatch.recordDepartureWeighing(weighing());

        assertThatThrownBy(() -> dispatch.recordDepartureWeighing(weighing()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", Dispatch.DEPARTURE_WEIGHING_ALREADY_RECORDED);
    }

    @Test
    void after_departing_nothing_of_the_preparation_changes() {
        Dispatch dispatch = prepared();
        dispatch.assignCarrier(carrier());
        dispatch.recordDepartureWeighing(weighing());
        dispatch.depart(NOW);

        assertThatThrownBy(() -> dispatch.assignCarrier(carrier())).isInstanceOf(InvalidDispatchTransitionException.class)
                .hasFieldOrPropertyWithValue("code", InvalidDispatchTransitionException.CODE);
        assertThatThrownBy(() -> dispatch.recordDepartureWeighing(weighing()))
                .isInstanceOf(InvalidDispatchTransitionException.class);
        assertThatThrownBy(() -> dispatch.depart(NOW)).isInstanceOf(InvalidDispatchTransitionException.class);
    }

    @Test
    void a_prepared_dispatch_is_not_received() {
        assertThatThrownBy(() -> prepared().markReceived(NOW)).isInstanceOf(InvalidDispatchTransitionException.class)
                .hasMessageContaining("Preparado");
    }

    @ParameterizedTest
    @EnumSource(DispatchStatus.class)
    void restores_every_state(DispatchStatus status) {
        UUID id = UUID.randomUUID();
        Dispatch restored = Dispatch.restore(id, ORDER, WAREHOUSE, WORKSITE, DispatchType.COMPLETE, CODE,
                List.of(line("5")), NOW, status, carrier(), weighing(), null, null, 4L);

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.status()).isEqualTo(status);
        assertThat(restored.version()).isEqualTo(4L);
        assertThat(status.label()).isNotBlank();
    }

    @Test
    void received_and_cancelled_admit_nothing() {
        for (DispatchStatus status : List.of(DispatchStatus.RECEIVED, DispatchStatus.CANCELLED)) {
            DispatchState state = DispatchState.of(status);
            assertThat(state.isEditable()).isFalse();
            assertThatThrownBy(state::depart).isInstanceOf(InvalidDispatchTransitionException.class);
            assertThatThrownBy(state::receive).isInstanceOf(InvalidDispatchTransitionException.class);
        }
        assertThat(DispatchType.COMPLETE.label()).isEqualTo("Completo");
        assertThat(DispatchType.PARTIAL.label()).isEqualTo("Parcial");
    }

    @ParameterizedTest
    @CsvSource({"'', 20555666777, ABC-123", "Rímac, 1234, ABC-123", "Rímac, 20555666777, A", "Rímac, 20555666777, ABCDE-12345"})
    void carrier_needs_name_document_and_plate(String name, String document, String plate) {
        assertThatThrownBy(() -> new Carrier(name, document, plate))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", Carrier.INVALID_CARRIER);
    }

    @Test
    void carrier_is_trimmed_and_the_plate_upper_cased() {
        Carrier carrier = carrier();
        assertThat(carrier.name()).isEqualTo("Transportes Rímac SAC");
        assertThat(carrier.plate()).isEqualTo("ABC-123");
        assertThatThrownBy(() -> new Carrier(null, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Carrier("x".repeat(151), "20555666777", "ABC-123")).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "100, 100", "100, 150", "-1, 0", "100, -1", "100.0001, 1"})
    void weighing_needs_a_positive_net(String gross, String tare) {
        assertThatThrownBy(() -> DepartureWeighing.of(new BigDecimal(gross), new BigDecimal(tare), null, NOW, ROSA))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", DepartureWeighing.INVALID_WEIGHING);
    }

    @Test
    void weighing_computes_the_net_and_keeps_the_evidence() {
        DepartureWeighing weighing = DepartureWeighing.of(new BigDecimal("1000"), BigDecimal.ZERO,
                " https://evidencias/ticket.jpg ", NOW, ROSA);

        assertThat(weighing.netKg()).isEqualByComparingTo("1000");
        assertThat(weighing.ticketPhotoUrl()).isEqualTo("https://evidencias/ticket.jpg");
        assertThat(DepartureWeighing.of(BigDecimal.TEN, BigDecimal.ONE, " ", NOW, ROSA).ticketPhotoUrl()).isNull();
        assertThatThrownBy(() -> new DepartureWeighing(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, null, NOW, ROSA))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DepartureWeighing.of(null, BigDecimal.ONE, null, NOW, ROSA))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void manifest_code_is_generated_with_the_date_and_eight_unambiguous_characters() {
        ManifestCode code = ManifestCode.generate(NOW, new Random(7));

        assertThat(code.value()).matches("MAN-20261103-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}");
        assertThat(ManifestCode.generate(NOW, new Random(8))).isNotEqualTo(code);
        assertThatThrownBy(() -> new ManifestCode("MAN-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "1.0001"})
    void dispatch_line_quantity_is_positive_with_three_decimals(String quantity) {
        assertThatThrownBy(() -> line(quantity))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
        assertThatThrownBy(() -> DispatchLine.of(UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(ValidationException.class);
    }
}
