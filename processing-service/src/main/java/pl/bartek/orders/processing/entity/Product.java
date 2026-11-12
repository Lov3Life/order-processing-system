package pl.bartek.orders.processing.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "product_id", nullable = false)
  private Long id;

  @Size(max = 200)
  @NotNull
  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @NotNull
  @ColumnDefault("0")
  @Column(name = "stock_quantity", nullable = false)
  private Integer stockQuantity;

  public Product() {
  }

  public Product(String name, Integer stockQuantity) {
    this.name = name;
    this.stockQuantity = stockQuantity;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public void addStockQuantity(Integer quantity) {
    this.stockQuantity += quantity;
  }

  public void subStockQuantity(Integer quantity) {
    this.stockQuantity -= quantity;
  }
}
