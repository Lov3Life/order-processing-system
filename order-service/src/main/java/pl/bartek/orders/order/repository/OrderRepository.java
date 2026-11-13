package pl.bartek.orders.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.enums.OrderStatus;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByCustomerId(Long customerId);


  @Modifying
  @Query("""
        UPDATE Order o
        SET o.orderStatus = :newStatus
        WHERE o.id = :orderId
          AND o.orderStatus = :expectedStatus
        """)
  int updateStatusIfPending(@Param("orderId") Long orderId,
                            @Param("newStatus") OrderStatus newStatus,
                            @Param("expectedStatus") OrderStatus expectedStatus);
}
