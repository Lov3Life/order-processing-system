package pl.bartek.orders.event;

import java.time.Instant;

public record OrderFinalizedEvent(
        Long orderId,
        ProcessingStatus status,
        String reason,
        Instant finalizedAt) {
}
