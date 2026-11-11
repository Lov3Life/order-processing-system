package pl.bartek.orders.order.exception;

public class OrderAlreadyProcessedException extends RuntimeException {
  public OrderAlreadyProcessedException(Long id) {
    super("Order with id " + id + " already processed.");
  }
}
