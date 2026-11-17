package pl.bartek.orders.outbox.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.enums.OutboxStatus;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

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
    when(jsonMapper.writeValueAsString(any(Object.class)))
            .thenReturn("it is payload");
    service.enqueue(1L, "orders.created", "{\"payload\":\"value\"}");
    verify(jsonMapper).writeValueAsString(any(Object.class));
    verify(repository).save(argThat(event ->
            event.getClass().equals(OutboxEvent.class) &&
            event.getAggregateId().equals(1L) &&
            event.getTopic().equals("orders.created") &&
            event.getAttempts() == 0 &&
            event.getStatus() == OutboxStatus.PENDING &&
            event.getPayload().equals("it is payload") &&
            event.getPublishedAt() == null &&
            event.getNextAttemptAt() != null
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
