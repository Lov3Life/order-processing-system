package pl.bartek.orders.notification.entity;

import jakarta.persistence.*;
import pl.bartek.orders.event.ProcessingStatus;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "notification_id", nullable = false)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProcessingStatus status;

  @Column(name = "message", nullable = false, length = 500)
  private String message;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Notification() {
  }

  public Notification(Long orderId, ProcessingStatus status, String message) {
    this.orderId = orderId;
    this.status = status;
    this.message = message;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public ProcessingStatus getStatus() {
    return status;
  }

  public void setStatus(ProcessingStatus status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
