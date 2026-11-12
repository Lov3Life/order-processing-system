package pl.bartek.orders.processing.exception;

public class ProductInvalidRequestDataException extends RuntimeException {
  public ProductInvalidRequestDataException(String message) {
    super(message);
  }
}
