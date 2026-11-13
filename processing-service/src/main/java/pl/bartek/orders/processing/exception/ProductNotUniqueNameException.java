package pl.bartek.orders.processing.exception;

public class ProductNotUniqueNameException extends RuntimeException {
  public ProductNotUniqueNameException(String name) {
    super("Product with name " + name + " is already exists.");
  }
}
