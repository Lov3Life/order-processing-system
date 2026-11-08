package pl.bartek.orders.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "order_id", nullable = false)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Size(max = 20)
  @NotNull
  @ColumnDefault("'PENDING'")
  @Column(name = "order_status", nullable = false, length = 20)
  private String orderStatus;

  @NotNull
  @ColumnDefault("CURRENT_TIMESTAMP(6)")
  @Column(name = "order_date", nullable = false)
  private Instant orderDate;
  @NonNull
  @OneToMany(mappedBy = "order")
  private List<OrderItem> orderItems = new ArrayList<>();


  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }

  public String getOrderStatus() {
    return orderStatus;
  }

  public void setOrderStatus(String orderStatus) {
    this.orderStatus = orderStatus;
  }

  public Instant getOrderDate() {
    return orderDate;
  }

  public void setOrderDate(Instant orderDate) {
    this.orderDate = orderDate;
  }

  @NonNull
  public List<OrderItem> getOrderItems() {
    return orderItems;
  }

  public void setOrderItems(@NonNull List<OrderItem> orderItems) {
    this.orderItems = orderItems;
  }
}
