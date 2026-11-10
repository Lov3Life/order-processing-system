package pl.bartek.orders.order.dto;

import pl.bartek.orders.order.entity.OrderItem;

public record OrderItemDTO(
        Long id,
        Long orderId,
        Long productId,
        Integer quantity) {
  public static OrderItemDTO fromOrderItem(OrderItem orderItem) {
    return new OrderItemDTO(
            orderItem.getId(),
            orderItem.getOrder().getId(),
            orderItem.getProductId(),
            orderItem.getQuantity());
  }
}
