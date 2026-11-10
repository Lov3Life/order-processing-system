package pl.bartek.orders.order.dto;

import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.entity.OrderItem;
import pl.bartek.orders.order.enums.OrderStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record OrderDTO(
        Long id,
        Long customerId,
        OrderStatus orderStatus,
        Instant orderDate,
        List<OrderItemDTO> items) {
  public static OrderDTO fromOrder(Order order) {
    List<OrderItemDTO> items = new ArrayList<>();
    for(OrderItem item : order.getOrderItems())
      items.add(OrderItemDTO.fromOrderItem(item));
    return new OrderDTO(
            order.getId(),
            order.getCustomer().getId(),
            order.getOrderStatus(),
            order.getOrderDate(),
            items);
  }
}
