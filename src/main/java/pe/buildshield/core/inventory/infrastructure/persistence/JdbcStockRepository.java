package pe.buildshield.core.inventory.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.shared.tenant.TenantContext;
import pe.buildshield.core.shared.tenant.TenantInfo;
import pe.buildshield.core.inventory.domain.model.Quantity;
import pe.buildshield.core.inventory.domain.model.StockItem;
import pe.buildshield.core.inventory.domain.model.StockMovement;
import pe.buildshield.core.inventory.domain.model.StockRepository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stock con SQL directo: el descuento necesita leer siempre lo último confirmado (sin la caché de
 * JPA) y un UPDATE condicionado por versión y cantidad. Como JDBC no pasa por el filtro multiempresa
 * de Hibernate, cada sentencia filtra por {@code organization_id} de forma explícita.
 */
@Repository
class JdbcStockRepository implements StockRepository {

    private static final RowMapper<StockItem> ITEM = (rs, rowNum) -> new StockItem(
            rs.getObject("id", UUID.class),
            rs.getObject("location_id", UUID.class),
            rs.getObject("material_id", UUID.class),
            rs.getBigDecimal("available_qty"),
            rs.getBigDecimal("reserved_qty"),
            rs.getLong("version"));

    private final JdbcTemplate jdbc;
    private final SpringDataStockItemRepository jpa;
    private final Clock clock;

    JdbcStockRepository(JdbcTemplate jdbc, SpringDataStockItemRepository jpa, Clock clock) {
        this.jdbc = jdbc;
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public Optional<StockItem> find(UUID locationId, UUID materialId) {
        return jdbc.query("""
                        SELECT id, location_id, material_id, available_qty, reserved_qty, version
                        FROM inventory.stock_items
                        WHERE organization_id = ? AND location_id = ? AND material_id = ?
                        """, ITEM, organizationId(), locationId, materialId)
                .stream().findFirst();
    }

    @Override
    public List<StockItem> findAll() {
        return jpa.findAllByOrderByLocationIdAscMaterialIdAsc().stream().map(StockItemJpaEntity::toDomain).toList();
    }

    @Override
    public boolean tryDeduct(UUID stockItemId, long expectedVersion, Quantity quantity) {
        TenantInfo tenant = TenantContext.require();
        int updated = jdbc.update("""
                        UPDATE inventory.stock_items
                        SET available_qty = available_qty - ?, version = version + 1, updated_at = ?, updated_by = ?
                        WHERE id = ? AND organization_id = ? AND version = ? AND available_qty >= ?
                        """,
                quantity.value(), Timestamp.from(clock.instant()), tenant.userId(),
                stockItemId, tenant.organizationId(), expectedVersion, quantity.value());
        return updated == 1;
    }

    @Override
    public StockItem add(UUID locationId, UUID materialId, Quantity quantity) {
        TenantInfo tenant = TenantContext.require();
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.queryForObject("""
                        INSERT INTO inventory.stock_items (id, organization_id, location_id, material_id, available_qty,
                                                           reserved_qty, version, created_at, created_by, updated_at, updated_by)
                        VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?)
                        ON CONFLICT (organization_id, location_id, material_id) DO UPDATE SET
                            available_qty = inventory.stock_items.available_qty + EXCLUDED.available_qty,
                            version = inventory.stock_items.version + 1,
                            updated_at = EXCLUDED.updated_at,
                            updated_by = EXCLUDED.updated_by
                        RETURNING id, location_id, material_id, available_qty, reserved_qty, version
                        """, ITEM,
                UUID.randomUUID(), tenant.organizationId(), locationId, materialId, quantity.value(),
                now, tenant.userId(), now, tenant.userId());
    }

    @Override
    public void record(StockMovement movement) {
        TenantInfo tenant = TenantContext.require();
        jdbc.update("""
                        INSERT INTO inventory.stock_movements (id, organization_id, stock_item_id, type, quantity,
                                                               balance_after, reference, occurred_at, recorded_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), tenant.organizationId(), movement.stockItemId(), movement.type().name(),
                movement.quantity(), movement.balanceAfter(), movement.reference(),
                Timestamp.from(movement.occurredAt()), tenant.userId());
    }

    private static UUID organizationId() {
        return TenantContext.require().organizationId();
    }
}
