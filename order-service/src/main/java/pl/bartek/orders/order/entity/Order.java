package pl.bartek.orders.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.NonNull;
import pl.bartek.orders.order.enums.OrderStatus;

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

  @NotNull
  @Enumerated(EnumType.STRING)
  @ColumnDefault("'PENDING'")
  @Column(name = "order_status", nullable = false, length = 20)
  private OrderStatus orderStatus;

  @NotNull
  @ColumnDefault("CURRENT_TIMESTAMP(6)")
  @Column(name = "order_date", nullable = false)
  private Instant orderDate;

  @NonNull
  @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
  private List<OrderItem> orderItems = new ArrayList<>();

  public Order() {
  }

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

  public OrderStatus getOrderStatus() {
    return orderStatus;
  }

  public void setOrderStatus(OrderStatus orderStatus) {
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

  public void addOrderItem(OrderItem orderItem) {
    orderItem.setOrder(this);
    this.orderItems.add(orderItem);
  }
}
