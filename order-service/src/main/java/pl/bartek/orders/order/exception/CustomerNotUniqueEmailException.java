package pl.bartek.orders.order.exception;

public class CustomerNotUniqueEmailException extends RuntimeException {
  public CustomerNotUniqueEmailException(String message) {
    super(message);
  }
}
