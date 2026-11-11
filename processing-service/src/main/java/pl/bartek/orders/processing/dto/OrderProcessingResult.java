package pl.bartek.orders.processing.dto;

import pl.bartek.orders.event.ProcessingStatus;

public record OrderProcessingResult(
        ProcessingStatus status,
        String reason) {
}
