package pl.bartek.orders.outbox.service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

public class OutboxService {

  private final OutboxEventRepository repository;
  private final JsonMapper jsonMapper;

  public OutboxService(OutboxEventRepository repository, JsonMapper jsonMapper) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(Long orderId, String topic, Object event) {
    String payload = jsonMapper.writeValueAsString(event);
    OutboxEvent outboxEvent = new OutboxEvent(
            orderId, event.getClass().getSimpleName(), topic, payload);
    repository.save(outboxEvent);
  }
}
