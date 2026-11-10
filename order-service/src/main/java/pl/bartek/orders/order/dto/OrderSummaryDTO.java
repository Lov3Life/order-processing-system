package pl.bartek.orders.order.dto;

import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.enums.OrderStatus;

import java.time.Instant;

public record OrderSummaryDTO(
        Long id,
        Long customerId,
        OrderStatus orderStatus,
        Instant orderDate) {
  public static OrderSummaryDTO fromOrder(Order order) {
    return new OrderSummaryDTO(
            order.getId(),
            order.getCustomer().getId(),
            order.getOrderStatus(),
            order.getOrderDate());
  }
}
