-- Sprint 2 (US22, US23, US24, US27, US30): despachos del almacén a la obra.
-- order_id, order_line_id, material_id, warehouse_id y worksite_id apuntan a otros esquemas: sin llave foránea.
CREATE TABLE dispatch.dispatches (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL,
    order_id         UUID         NOT NULL,
    warehouse_id     UUID         NOT NULL,
    worksite_id      UUID         NOT NULL,
    type             VARCHAR(10)  NOT NULL,
    status           VARCHAR(15)  NOT NULL,
    manifest_code    VARCHAR(30)  NOT NULL,
    carrier_name     VARCHAR(150),
    carrier_document VARCHAR(20),
    plate            VARCHAR(10),
    prepared_at      TIMESTAMPTZ  NOT NULL,
    departed_at      TIMESTAMPTZ,
    received_at      TIMESTAMPTZ,
    version          BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID         NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       UUID         NOT NULL,
    CONSTRAINT uk_dispatches_manifest_code UNIQUE (manifest_code),
    CONSTRAINT uk_dispatches_org_id UNIQUE (organization_id, id),
    CONSTRAINT ck_dispatches_type CHECK (type IN ('COMPLETE', 'PARTIAL')),
    CONSTRAINT ck_dispatches_status CHECK (status IN ('PREPARED', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED')),
    -- Un despacho no sale sin transportista.
    CONSTRAINT ck_dispatches_carrier CHECK (status IN ('PREPARED', 'CANCELLED')
        OR (carrier_name IS NOT NULL AND carrier_document IS NOT NULL AND plate IS NOT NULL AND departed_at IS NOT NULL))
);

CREATE INDEX idx_dispatches_order ON dispatch.dispatches (organization_id, order_id);
CREATE INDEX idx_dispatches_warehouse ON dispatch.dispatches (organization_id, warehouse_id);
CREATE INDEX idx_dispatches_worksite ON dispatch.dispatches (organization_id, worksite_id);

CREATE TABLE dispatch.dispatch_lines (
    id              UUID PRIMARY KEY,
    organization_id UUID           NOT NULL,
    dispatch_id     UUID           NOT NULL,
    order_line_id   UUID           NOT NULL,
    material_id     UUID           NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    CONSTRAINT uk_dispatch_lines_order_line UNIQUE (dispatch_id, order_line_id),
    CONSTRAINT fk_dispatch_lines_org_dispatch
        FOREIGN KEY (organization_id, dispatch_id) REFERENCES dispatch.dispatches (organization_id, id),
    CONSTRAINT ck_dispatch_lines_quantity CHECK (quantity > 0)
);

-- Pesaje de salida: uno por despacho. La foto del ticket es una referencia a la evidencia guardada.
CREATE TABLE dispatch.departure_weighings (
    id               UUID PRIMARY KEY,
    organization_id  UUID           NOT NULL,
    dispatch_id      UUID           NOT NULL,
    gross_kg         NUMERIC(12, 3) NOT NULL,
    tare_kg          NUMERIC(12, 3) NOT NULL,
    net_kg           NUMERIC(12, 3) NOT NULL,
    ticket_photo_url VARCHAR(500),
    weighed_at       TIMESTAMPTZ    NOT NULL,
    weighed_by       UUID           NOT NULL,
    CONSTRAINT uk_departure_weighings_dispatch UNIQUE (dispatch_id),
    CONSTRAINT fk_departure_weighings_org_dispatch
        FOREIGN KEY (organization_id, dispatch_id) REFERENCES dispatch.dispatches (organization_id, id),
    CONSTRAINT ck_departure_weighings_weights CHECK (gross_kg > 0 AND tare_kg >= 0 AND net_kg > 0
        AND net_kg = gross_kg - tare_kg)
);
