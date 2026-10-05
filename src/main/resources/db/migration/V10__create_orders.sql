-- Módulo ordering: pedidos de materiales de obra a almacén.
-- worksite_id, warehouse_id, material_id y requested_by apuntan a otros esquemas: sin llave foránea.
CREATE TABLE ordering.orders (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL,
    worksite_id      UUID         NOT NULL,
    warehouse_id     UUID         NOT NULL,
    requested_by     UUID         NOT NULL,
    notes            VARCHAR(500),
    placed_at        TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    rejection_reason VARCHAR(500),
    decided_by       UUID,
    decided_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID         NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       UUID         NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('REGISTERED', 'IN_REVIEW', 'PARTIALLY_FULFILLED', 'FULFILLED',
                                                  'CLOSED', 'CANCELLED'))
);

CREATE INDEX idx_orders_worksite ON ordering.orders (organization_id, worksite_id);
CREATE INDEX idx_orders_warehouse ON ordering.orders (organization_id, warehouse_id);

CREATE TABLE ordering.order_lines (
    id              UUID PRIMARY KEY,
    organization_id UUID           NOT NULL,
    order_id        UUID           NOT NULL REFERENCES ordering.orders (id),
    material_id     UUID           NOT NULL,
    sku             VARCHAR(40)    NOT NULL,
    unit            VARCHAR(10)    NOT NULL,
    requested_qty   NUMERIC(14, 3) NOT NULL,
    dispatched_qty  NUMERIC(14, 3) NOT NULL,
    cancelled_qty   NUMERIC(14, 3) NOT NULL,
    received_qty    NUMERIC(14, 3) NOT NULL,
    CONSTRAINT uk_order_lines_material UNIQUE (order_id, material_id),
    -- pendiente = solicitado - despachado - cancelado, nunca negativo.
    CONSTRAINT ck_order_lines_quantities CHECK (
        requested_qty > 0 AND dispatched_qty >= 0 AND cancelled_qty >= 0 AND received_qty >= 0
        AND dispatched_qty + cancelled_qty <= requested_qty AND received_qty <= dispatched_qty)
);
