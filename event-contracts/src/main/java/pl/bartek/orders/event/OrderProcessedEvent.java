package pl.bartek.orders.event;

public record OrderProcessedEvent(
        Long orderId,
        ProcessingStatus status,
        String reason) {
}
