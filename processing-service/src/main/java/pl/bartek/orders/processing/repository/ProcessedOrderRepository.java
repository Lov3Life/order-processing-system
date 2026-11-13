package pl.bartek.orders.processing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.bartek.orders.processing.entity.ProcessedOrder;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {

  @Modifying
  @Query(value = """
            INSERT IGNORE INTO processed_orders
                (order_id, status)
            VALUES (:orderId, 'PROCESSING')
            """, nativeQuery = true)
  int tryClaim(@Param("orderId") Long orderId);

  @Modifying
  @Query(value = """
            UPDATE processed_orders
            SET status = :status,
                reason = :reason,
                processed_at = UTC_TIMESTAMP(6)
            WHERE order_id = :orderId
            """, nativeQuery = true)
  int markProcessed(@Param("orderId") Long orderId,
                    @Param("status") String status,
                    @Param("reason") String reason);
}
