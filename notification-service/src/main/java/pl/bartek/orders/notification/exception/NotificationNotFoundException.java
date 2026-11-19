package pl.bartek.orders.notification.exception;

public class NotificationNotFoundException extends RuntimeException {
  public NotificationNotFoundException(Long notificationId) {
    super("Notification with id " + notificationId + " not found.");
  }
}
