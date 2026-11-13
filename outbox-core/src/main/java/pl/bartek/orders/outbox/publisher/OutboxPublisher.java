package pl.bartek.orders.outbox.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.enums.OutboxStatus;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.outbox.service.OutboxStatusService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OutboxPublisher {

  private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository repository;
  private final OutboxStatusService statusService;
  private final KafkaTemplate<String, String> kafkaTemplate;

  public OutboxPublisher(OutboxEventRepository repository,
                         OutboxStatusService statusService,
                         KafkaTemplate<String, String> kafkaTemplate) {
    this.repository = repository;
    this.statusService = statusService;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}")
  public void publishPendingEvents() {

    List<OutboxEvent> events = repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus.PENDING, Instant.now());
    for (OutboxEvent event : events) {
      try {
        kafkaTemplate.send(
                event.getTopic(), event.getAggregateId().toString(), event.getPayload()).get(10, TimeUnit.SECONDS);
        statusService.markSent(event.getId());
        logger.info("Published outbox event {}", event.getId());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception exception) {
        logger.error("Failed to publish outbox event {}", event.getId(), exception);
        statusService.markFailedAttempt(event.getId());
      }
    }
  }
}
