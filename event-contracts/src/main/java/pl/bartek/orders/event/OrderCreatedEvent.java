package pl.bartek.orders.event;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        List<OrderItemEvent> items) {
}
