package pe.buildshield.core.organization.domain.model;

import org.junit.jupiter.api.Test;
import pe.buildshield.core.shared.error.ValidationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseTest {

    @Test
    void registers_active() {
        Warehouse warehouse = Warehouse.register(" Almacén Central ", WarehouseType.WAREHOUSE, " Av. Argentina 2500 ");

        assertThat(warehouse.active()).isTrue();
        assertThat(warehouse.name()).isEqualTo("Almacén Central");
        assertThat(warehouse.address()).isEqualTo("Av. Argentina 2500");
        assertThat(warehouse.type()).isEqualTo(WarehouseType.WAREHOUSE);
        assertThat(warehouse.id()).isNull();
    }

    @Test
    void requires_name_type_and_address() {
        assertThatThrownBy(() -> Warehouse.register("Central", null, "Av. 1"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_WAREHOUSE_TYPE");
        assertThatThrownBy(() -> Warehouse.register(" ", WarehouseType.WAREHOUSE, "Av. 1"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Warehouse.register("Central", WarehouseType.WAREHOUSE, " "))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ADDRESS");
        assertThatThrownBy(() -> Warehouse.register("Central", WarehouseType.WAREHOUSE, "x".repeat(201)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void deactivates_and_reactivates_keeping_its_data() {
        Warehouse warehouse = Warehouse.restore(UUID.randomUUID(), "Central", WarehouseType.WAREHOUSE, "Av. 1", true, 3L);

        warehouse.deactivate();
        assertThat(warehouse.active()).isFalse();
        assertThat(warehouse.name()).isEqualTo("Central");

        warehouse.activate();
        assertThat(warehouse.active()).isTrue();
        assertThat(warehouse.version()).isEqualTo(3L);
    }

    @Test
    void update_changes_only_given_fields() {
        Warehouse warehouse = Warehouse.restore(UUID.randomUUID(), "Central", WarehouseType.WAREHOUSE, "Av. 1", true, 0L);

        warehouse.update(null, WarehouseType.COLLECTION_CENTER, null);
        assertThat(warehouse.type()).isEqualTo(WarehouseType.COLLECTION_CENTER);
        assertThat(warehouse.name()).isEqualTo("Central");

        warehouse.update("Acopio Chosica", null, "Carretera Central km 34");
        assertThat(warehouse.name()).isEqualTo("Acopio Chosica");
        assertThat(warehouse.address()).isEqualTo("Carretera Central km 34");

        assertThatThrownBy(() -> warehouse.update("Otro", null, " ")).isInstanceOf(ValidationException.class);
        assertThat(warehouse.name()).as("un cambio rechazado no modifica nada").isEqualTo("Acopio Chosica");
    }

    @Test
    void types_have_spanish_names() {
        assertThat(WarehouseType.COLLECTION_CENTER.displayName()).isEqualTo("centro de acopio");
        assertThat(WarehouseType.WAREHOUSE.displayName()).isEqualTo("almacén");
    }
}
