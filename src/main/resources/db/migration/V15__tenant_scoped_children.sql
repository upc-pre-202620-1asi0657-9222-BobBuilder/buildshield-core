-- Relaciones solo dentro del módulo; no hay FK entre BC.
-- Si hay datos históricos incompatibles, la migración falla sin corregirlos silenciosamente.
ALTER TABLE inventory.stock_items ADD CONSTRAINT uk_stock_items_org_id UNIQUE (organization_id, id);
ALTER TABLE inventory.stock_movements ADD CONSTRAINT fk_stock_movements_org_item
    FOREIGN KEY (organization_id, stock_item_id) REFERENCES inventory.stock_items (organization_id, id);
ALTER TABLE ordering.orders ADD CONSTRAINT uk_orders_org_id UNIQUE (organization_id, id);
ALTER TABLE ordering.order_lines ADD CONSTRAINT fk_order_lines_org_order
    FOREIGN KEY (organization_id, order_id) REFERENCES ordering.orders (organization_id, id);
