-- Sprint 2 (US35, US37, QAS01, QAS10): recepción en obra, cotejo y conformidad.
-- dispatch_id, dispatch_line_id, order_id, order_line_id, material_id y los lugares apuntan a otros
-- esquemas: sin llave foránea.
CREATE TABLE reception.receptions (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    dispatch_id     UUID        NOT NULL,
    order_id        UUID        NOT NULL,
    warehouse_id    UUID        NOT NULL,
    worksite_id     UUID        NOT NULL,
    status          VARCHAR(15) NOT NULL,
    confirmed_by    UUID,
    confirmed_at    TIMESTAMPTZ,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID        NOT NULL,
    -- Una sola recepción por despacho (QAS10).
    CONSTRAINT uk_receptions_dispatch UNIQUE (dispatch_id),
    CONSTRAINT uk_receptions_org_id UNIQUE (organization_id, id),
    CONSTRAINT ck_receptions_status CHECK (status IN ('IN_PROGRESS', 'CONFIRMED')),
    CONSTRAINT ck_receptions_confirmation CHECK (status <> 'CONFIRMED'
        OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE INDEX idx_receptions_worksite ON reception.receptions (organization_id, worksite_id);

CREATE TABLE reception.reception_lines (
    id               UUID PRIMARY KEY,
    organization_id  UUID           NOT NULL,
    reception_id     UUID           NOT NULL,
    dispatch_line_id UUID           NOT NULL,
    order_line_id    UUID           NOT NULL,
    material_id      UUID           NOT NULL,
    dispatched_qty   NUMERIC(14, 3) NOT NULL,
    received_qty     NUMERIC(14, 3),
    shrinkage_pct    NUMERIC(6, 2),
    CONSTRAINT uk_reception_lines_dispatch_line UNIQUE (reception_id, dispatch_line_id),
    CONSTRAINT fk_reception_lines_org_reception
        FOREIGN KEY (organization_id, reception_id) REFERENCES reception.receptions (organization_id, id),
    CONSTRAINT ck_reception_lines_quantities CHECK (dispatched_qty > 0
        AND (received_qty IS NULL OR (received_qty >= 0 AND received_qty <= dispatched_qty)))
);
