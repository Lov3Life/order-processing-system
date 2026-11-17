package pl.bartek.orders.outbox.publisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.enums.OutboxStatus;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.outbox.service.OutboxStatusService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherTest {

  @Mock
  private OutboxEventRepository repository;

  @Mock
  private OutboxStatusService statusService;

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  private OutboxPublisher outboxPublisher;

  @BeforeEach
  void setUp() {
    outboxPublisher = new OutboxPublisher(repository, statusService, kafkaTemplate);
  }

  @Test
  void shouldPublishPendingEvents() {
    OutboxEvent event = new OutboxEvent(1L, "OrderCreatedEvent", "orders.created", "payload");
    when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(OutboxStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(event));
    when(kafkaTemplate.send("orders.created", "1", "payload"))
            .thenReturn(CompletableFuture.completedFuture(null));
    outboxPublisher.publishPendingEvents();
    verify(kafkaTemplate).send("orders.created", "1", "payload");
    verify(statusService).markSent(event.getId());
    verify(statusService, never()).markFailedAttempt(anyString());
  }

  @Test
  void shouldMarkFailedAttemptWhenKafkaSendFails() {
    OutboxEvent event = new OutboxEvent(1L, "OrderCreatedEvent", "orders.created", "payload");
    when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(OutboxStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(event));
    when(kafkaTemplate.send("orders.created", "1", "payload"))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    outboxPublisher.publishPendingEvents();
    verify(kafkaTemplate).send("orders.created", "1", "payload");
    verify(statusService).markFailedAttempt(event.getId());
    verify(statusService, never()).markSent(anyString());
  }

  @Test
  void shouldNotPublishWhenNoPendingEvents() {
    when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(OutboxStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of());
    outboxPublisher.publishPendingEvents();
    verifyNoInteractions(kafkaTemplate, statusService);
  }

  @Test
  void shouldContinuePublishingAfterSingleFailure() {
    OutboxEvent event1 = new OutboxEvent(1L, "OrderCreatedEvent", "orders.created", "payload-1");
    OutboxEvent event2 = new OutboxEvent(2L, "OrderCreatedEvent", "orders.created", "payload-2");
    when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(OutboxStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(event1, event2));
    when(kafkaTemplate.send("orders.created", "1", "payload-1"))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    when(kafkaTemplate.send("orders.created", "2", "payload-2"))
            .thenReturn(CompletableFuture.completedFuture(null));
    outboxPublisher.publishPendingEvents();
    verify(kafkaTemplate).send("orders.created", "1", "payload-1");
    verify(kafkaTemplate).send("orders.created", "2", "payload-2");
    verify(statusService).markFailedAttempt(event1.getId());
    verify(statusService).markSent(event2.getId());
    verify(statusService, never()).markSent(event1.getId());
    verify(statusService, never()).markFailedAttempt(event2.getId());
    InOrder inOrder = inOrder(statusService);
    inOrder.verify(statusService).markFailedAttempt(event1.getId());
    inOrder.verify(statusService).markSent(event2.getId());
  }
}
