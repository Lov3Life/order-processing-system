package pl.bartek.orders.notification.exception;

public class NotificationInvalidDataException extends RuntimeException {
  public NotificationInvalidDataException(String message) {
    super(message);
  }
}
