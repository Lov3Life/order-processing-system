package pl.bartek.orders.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.bartek.orders.order.entity.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByCustomerId(Long customerId);

  List<Order> id(Long id);
}
