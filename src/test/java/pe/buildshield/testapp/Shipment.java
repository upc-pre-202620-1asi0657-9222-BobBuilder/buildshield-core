package pe.buildshield.testapp;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.buildshield.core.shared.persistence.AuditableAbstractAggregateRoot;

import java.util.UUID;

/** Agregado de prueba auditable. */
@Entity
@Table(name = "test_shipments")
public class Shipment extends AuditableAbstractAggregateRoot<Shipment> {

    private String status = "CREATED";

    public void dispatch() {
        status = "DISPATCHED";
        registerEvent(new ShipmentDispatched(getId()));
    }

    public void rename(String newStatus) {
        status = newStatus;
    }

    public String getStatus() {
        return status;
    }

    public record ShipmentDispatched(UUID shipmentId) {
    }
}
