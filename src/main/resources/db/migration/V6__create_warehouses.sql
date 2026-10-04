-- US15: almacenes y centros de acopio. No se borran: se desactivan (active = false).
CREATE TABLE organization.warehouses (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    address         VARCHAR(200) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID         NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      UUID         NOT NULL,
    CONSTRAINT ck_warehouses_type CHECK (type IN ('WAREHOUSE', 'COLLECTION_CENTER'))
);

CREATE INDEX idx_warehouses_organization ON organization.warehouses (organization_id);
