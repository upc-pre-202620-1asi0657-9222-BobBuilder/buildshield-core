-- Sprint 2 (T29, RF40/RF41, QAS02): al aprobar un pedido se reserva lo solicitado en el almacén de
-- origen (disponible -> reservado) y al despachar se consume la reserva (reservado -> salida).
-- order_id y order_line_id están en el esquema ordering: sin llave foránea.
CREATE TABLE inventory.stock_reservations (
    id              UUID PRIMARY KEY,
    organization_id UUID           NOT NULL,
    stock_item_id   UUID           NOT NULL,
    order_id        UUID           NOT NULL,
    order_line_id   UUID           NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    consumed_qty    NUMERIC(14, 3) NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    reserved_at     TIMESTAMPTZ    NOT NULL,
    reserved_by     UUID           NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    -- Una sola reserva por línea de pedido.
    CONSTRAINT uk_stock_reservations_order_line UNIQUE (organization_id, order_line_id),
    CONSTRAINT fk_stock_reservations_org_item
        FOREIGN KEY (organization_id, stock_item_id) REFERENCES inventory.stock_items (organization_id, id),
    CONSTRAINT ck_stock_reservations_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
    -- Nunca se consume más de lo reservado.
    CONSTRAINT ck_stock_reservations_quantities CHECK (quantity > 0 AND consumed_qty >= 0 AND consumed_qty <= quantity)
);

CREATE INDEX idx_stock_reservations_order ON inventory.stock_reservations (organization_id, order_id);

-- Nuevos tipos de movimiento: RESERVE (disponible -> reservado), RELEASE (reservado -> disponible)
-- y DISPATCH (salida de lo reservado al despachar).
ALTER TABLE inventory.stock_movements DROP CONSTRAINT ck_stock_movements_type;
ALTER TABLE inventory.stock_movements ADD CONSTRAINT ck_stock_movements_type
    CHECK (type IN ('ENTRY', 'DEDUCTION', 'RESERVE', 'RELEASE', 'DISPATCH'));
