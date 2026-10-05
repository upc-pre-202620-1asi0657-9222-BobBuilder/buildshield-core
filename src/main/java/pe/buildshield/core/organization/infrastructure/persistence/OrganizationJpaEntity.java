package pe.buildshield.core.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla organization.organizations. No usa {@code OrganizationScopedEntity}: la organización es el
 * propio tenant, su {@code id} es el {@code organization_id} del resto de tablas.
 */
@Entity
@Table(schema = "organization", name = "organizations")
public class OrganizationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "ruc", nullable = false, length = 11)
    private String ruc;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationJpaEntity() {
    }

    OrganizationJpaEntity(UUID id, String ruc, String legalName) {
        this.id = id;
        this.ruc = ruc;
        this.legalName = legalName;
    }

    UUID getId() {
        return id;
    }

    String getRuc() {
        return ruc;
    }

    String getLegalName() {
        return legalName;
    }
}
