package pe.buildshield.core.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.buildshield.core.shared.persistence.AuditableAbstractAggregateRoot;
import pe.buildshield.core.inventory.domain.model.StockItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tabla inventory.stock_items. La columna {@code version} (bloqueo optimista, heredada con
 * {@code @Version}) es la que condiciona el UPDATE de descuento en {@link JdbcStockRepository}.
 * Por JPA solo se lee (con el filtro multiempresa).
 */
@Entity
@Table(schema = "inventory", name = "stock_items")
public class StockItemJpaEntity extends AuditableAbstractAggregateRoot<StockItemJpaEntity> {

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "available_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal availableQty;

    @Column(name = "reserved_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedQty;

    protected StockItemJpaEntity() {
    }

    StockItem toDomain() {
        return new StockItem(getId(), locationId, materialId, availableQty, reservedQty, getVersion());
    }
}
