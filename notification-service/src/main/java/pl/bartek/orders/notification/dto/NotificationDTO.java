package pl.bartek.orders.notification.dto;

import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.notification.entity.Notification;

import java.time.Instant;

public record NotificationDTO(
        Long id,
        Long orderId,
        ProcessingStatus status,
        String message,
        Instant createdAt) {
  public static NotificationDTO FromNotification(Notification notification) {
    return new NotificationDTO(
            notification.getId(),
            notification.getOrderId(),
            notification.getStatus(),
            notification.getMessage(),
            notification.getCreatedAt());
  }
}
