package pl.bartek.orders.outbox.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderItemEvent;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.enums.OutboxStatus;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxServiceTest {

  @Mock
  private OutboxEventRepository repository;

  @Mock
  private JsonMapper jsonMapper;

  private OutboxService service;

  @BeforeEach
  void setUp() {
    service = new OutboxService(repository, jsonMapper);
  }

  @Test
  void shouldEnqueueOutboxEvent() {
    OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(1L, List.of(
            new OrderItemEvent(1L, 3), new OrderItemEvent(6L, 1)
    ));
    when(jsonMapper.writeValueAsString(any(Object.class)))
            .thenReturn("it is payload");
    service.enqueue(1L, "orders.created", orderCreatedEvent);
    verify(jsonMapper).writeValueAsString(argThat(event ->
            event.getClass().equals(OrderCreatedEvent.class)
    ));
    verify(repository).save(argThat(event ->
            event.getClass().equals(OutboxEvent.class) &&
            event.getAggregateId().equals(1L) &&
            event.getTopic().equals("orders.created") &&
            event.getAttempts() == 0 &&
            event.getStatus() == OutboxStatus.PENDING &&
            event.getPayload().equals("it is payload") &&
            event.getPublishedAt() == null &&
            Objects.equals(event.getCreatedAt(), event.getNextAttemptAt()) &&
            Objects.equals(event.getEventType(), "OrderCreatedEvent")
    ));
  }

  @Test
  void shouldNotSaveOutboxEventWhenSerializationFails() {
    when(jsonMapper.writeValueAsString(any(Object.class)))
            .thenThrow(new RuntimeException());
    Assertions.assertThrows(RuntimeException.class,
            () -> service.enqueue(1L, "orders.created", "{}"));
    verifyNoInteractions(repository);
  }

}
