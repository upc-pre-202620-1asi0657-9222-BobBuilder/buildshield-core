-- Módulo inventory: existencias por almacén y material, y sus movimientos.
-- location_id (almacén) y material_id están en el esquema organization: sin llave foránea.
CREATE TABLE inventory.stock_items (
    id              UUID PRIMARY KEY,
    organization_id UUID           NOT NULL,
    location_id     UUID           NOT NULL,
    material_id     UUID           NOT NULL,
    available_qty   NUMERIC(14, 3) NOT NULL,
    reserved_qty    NUMERIC(14, 3) NOT NULL,
    version         BIGINT         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    created_by      UUID           NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    updated_by      UUID           NOT NULL,
    CONSTRAINT uk_stock_items_location_material UNIQUE (organization_id, location_id, material_id),
    -- QAS02: última defensa; el inventario nunca queda negativo aunque falle la aplicación.
    CONSTRAINT ck_stock_items_non_negative CHECK (available_qty >= 0 AND reserved_qty >= 0)
);

-- Movimientos: solo se agregan, nunca se modifican.
CREATE TABLE inventory.stock_movements (
    id              UUID PRIMARY KEY,
    organization_id UUID           NOT NULL,
    stock_item_id   UUID           NOT NULL REFERENCES inventory.stock_items (id),
    type            VARCHAR(10)    NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    balance_after   NUMERIC(14, 3) NOT NULL,
    reference       VARCHAR(200),
    occurred_at     TIMESTAMPTZ    NOT NULL,
    recorded_by     UUID           NOT NULL,
    CONSTRAINT ck_stock_movements_type CHECK (type IN ('ENTRY', 'DEDUCTION')),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity > 0 AND balance_after >= 0)
);

CREATE INDEX idx_stock_movements_item ON inventory.stock_movements (stock_item_id, occurred_at);
