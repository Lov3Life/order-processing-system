package pl.bartek.orders.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "customer_id", nullable = false)
  private Long id;

  @Size(max = 100)
  @NotNull
  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Size(max = 100)
  @NotNull
  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Size(max = 255)
  @NotNull
  @Column(name = "email", nullable = false)
  private String email;

  @NonNull
  @OneToMany(mappedBy = "customer")
  private List<Order> orders = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @NonNull
  public List<Order> getOrders() {
    return orders;
  }

  public void setOrders(@NonNull List<Order> orders) {
    this.orders = orders;
  }
}
