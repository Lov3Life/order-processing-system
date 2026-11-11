package pl.bartek.orders.event;

public record OrderItemEvent(
        Long productId,
        Integer quantity) {
}
