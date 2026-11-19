package pl.bartek.orders.notification.exception;

public class NotificationInvalidData extends RuntimeException {
  public NotificationInvalidData(String message) {
    super(message);
  }
}
