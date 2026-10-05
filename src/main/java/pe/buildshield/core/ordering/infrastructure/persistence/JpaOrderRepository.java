package pe.buildshield.core.ordering.infrastructure.persistence;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import pe.buildshield.core.shared.error.ResourceNotFoundException;
import pe.buildshield.core.ordering.domain.model.Order;
import pe.buildshield.core.ordering.domain.model.OrderRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository jpa;

    JpaOrderRepository(SpringDataOrderRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findAll() {
        return jpa.findAllByOrderByPlacedAtDesc().stream().map(OrderJpaEntity::toDomain).toList();
    }

    @Override
    public List<Order> findBySites(Collection<UUID> siteIds) {
        if (siteIds.isEmpty()) {
            return List.of();
        }
        return jpa.findBySites(siteIds).stream().map(OrderJpaEntity::toDomain).toList();
    }

    @Override
    public Order save(Order order) {
        if (order.id() == null) {
            return jpa.saveAndFlush(new OrderJpaEntity(order)).toDomain();
        }
        OrderJpaEntity entity = jpa.findById(order.id())
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND", "El pedido no existe"));
        if (!Objects.equals(entity.getVersion(), order.version())) {
            throw new ObjectOptimisticLockingFailureException(OrderJpaEntity.class, order.id());
        }
        entity.apply(order);
        return jpa.saveAndFlush(entity).toDomain();
    }
}
