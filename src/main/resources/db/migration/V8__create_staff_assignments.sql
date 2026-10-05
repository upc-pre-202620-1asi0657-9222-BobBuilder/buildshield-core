-- US17: asignación de encargados a obras y almacenes. Al terminar se conserva (ended_at) como historial.
-- user_id apunta a iam.users: sin llave foránea porque es otro esquema.
CREATE TABLE organization.staff_assignments (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    site_type       VARCHAR(20) NOT NULL,
    worksite_id     UUID REFERENCES organization.worksites (id),
    warehouse_id    UUID REFERENCES organization.warehouses (id),
    assigned_at     TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID        NOT NULL,
    CONSTRAINT ck_staff_assignments_site CHECK (
        (site_type = 'WORKSITE' AND worksite_id IS NOT NULL AND warehouse_id IS NULL)
        OR (site_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL AND worksite_id IS NULL)),
    CONSTRAINT ck_staff_assignments_dates CHECK (ended_at IS NULL OR ended_at >= assigned_at)
);

-- Una sola asignación activa por usuario y lugar.
CREATE UNIQUE INDEX uk_staff_assignments_active_worksite
    ON organization.staff_assignments (user_id, worksite_id) WHERE ended_at IS NULL AND worksite_id IS NOT NULL;
CREATE UNIQUE INDEX uk_staff_assignments_active_warehouse
    ON organization.staff_assignments (user_id, warehouse_id) WHERE ended_at IS NULL AND warehouse_id IS NOT NULL;

CREATE INDEX idx_staff_assignments_user ON organization.staff_assignments (user_id) WHERE ended_at IS NULL;
