package pl.bartek.orders.processing.dto;

import pl.bartek.orders.processing.entity.Product;

public record ProductDTO(
        Long id,
        String name,
        Integer stockQuantity) {
  public static ProductDTO fromProduct(Product product) {
    return new ProductDTO(product.getId(), product.getName(), product.getStockQuantity());
  }
}
