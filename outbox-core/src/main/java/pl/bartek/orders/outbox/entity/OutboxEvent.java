package pl.bartek.orders.outbox.entity;

import jakarta.persistence.*;
import pl.bartek.orders.outbox.enums.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OutboxStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxEvent() {
  }

  public OutboxEvent(Long aggregateId, String eventType,
                     String topic, String payload) {
    this.id = UUID.randomUUID().toString();
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.topic = topic;
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
    this.attempts = 0;
    this.createdAt = Instant.now();
    this.nextAttemptAt = this.createdAt;
  }

  public void markSent() {
    this.status = OutboxStatus.SENT;
    this.publishedAt = Instant.now();
  }

  public void markFailedAttempt() {
    this.attempts++;
    if(attempts >= 10) {
      this.status = OutboxStatus.FAILED;
    } else {
      long delay = Math.min(300, attempts * 10L);
      this.nextAttemptAt = Instant.now().plusSeconds(delay);
    }
  }

  public String getId() {
    return id;
  }

  public Long getAggregateId() {
    return aggregateId;
  }

  public String getTopic() {
    return topic;
  }

  public String getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public int getAttempts() {
    return attempts;
  }
}
