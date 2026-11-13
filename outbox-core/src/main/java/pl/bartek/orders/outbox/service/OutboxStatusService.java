package pl.bartek.orders.outbox.service;

import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;

public class OutboxStatusService {

  private final OutboxEventRepository repository;

  public OutboxStatusService(OutboxEventRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void markSent(String eventId) {
    OutboxEvent event = repository.findById(eventId)
            .orElseThrow();
    event.markSent();
  }

  @Transactional
  public void markFailedAttempt(String eventId) {
    OutboxEvent event = repository.findById(eventId)
            .orElseThrow();
    event.markFailedAttempt();
  }
}
