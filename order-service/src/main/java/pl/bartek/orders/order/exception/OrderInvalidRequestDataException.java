package pl.bartek.orders.order.exception;

public class OrderInvalidRequestDataException extends RuntimeException {
  public OrderInvalidRequestDataException(String message) {
    super(message);
  }
}
