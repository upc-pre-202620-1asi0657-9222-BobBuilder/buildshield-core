package pe.buildshield.testapp;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.core.shared.persistence.OrganizationScopedEntity;

import java.util.UUID;

/** Entidad de prueba con alcance de organización. */
@Entity
@Table(name = "test_notes")
public class Note extends OrganizationScopedEntity {

    @Id
    private UUID id;

    private String text;

    protected Note() {
    }

    public Note(String text) {
        this.id = UUID.randomUUID();
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}
