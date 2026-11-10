package pl.bartek.orders.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.bartek.orders.order.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
