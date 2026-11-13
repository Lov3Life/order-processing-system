package pl.bartek.orders.processing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "processed_orders")
public class ProcessedOrder {

  @Id
  @Column(name = "order_id", nullable = false)
  private Long id;

  @Size(max = 20)
  @NotNull
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Size(max = 255)
  @Column(name = "reason")
  private String reason;

  @NotNull
  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedOrder() {
  }

  public Long getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
