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

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OutboxStatusServiceTest {

  @Mock
  private OutboxEventRepository outboxEventRepository;

  private OutboxStatusService outboxStatusService;

  @BeforeEach
  void setUp() {
    outboxStatusService = new OutboxStatusService(outboxEventRepository);
  }

  @Test
  void shouldMarkOutboxEventAsSent() {
    OutboxEvent event = new OutboxEvent(1L, "type", "orders.created", "payload");
    when(outboxEventRepository.findById(any(String.class)))
            .thenReturn(Optional.of(event));
    outboxStatusService.markSent(event.getId());
    Assertions.assertEquals(OutboxStatus.SENT, event.getStatus());
    Assertions.assertNotNull(event.getPublishedAt());
    Assertions.assertEquals(0, event.getAttempts());
    verify(outboxEventRepository).findById(event.getId());
  }

  @Test
  void shouldIncrementAttemptsAfterFailure() {
    OutboxEvent event = new OutboxEvent(1L, "type", "orders.created", "payload");
    Instant createdNextAttempt = event.getNextAttemptAt();
    when(outboxEventRepository.findById(any(String.class)))
            .thenReturn(Optional.of(event));
    outboxStatusService.markFailedAttempt(event.getId());
    Assertions.assertEquals(OutboxStatus.PENDING, event.getStatus());
    Assertions.assertNull(event.getPublishedAt());
    Assertions.assertTrue(event.getNextAttemptAt().isAfter(createdNextAttempt));
    Assertions.assertEquals(1, event.getAttempts());
    verify(outboxEventRepository).findById(event.getId());
  }

  @Test
  void shouldThrowExceptionWhenEventNotFoundWhenSent() {
    when(outboxEventRepository.findById(any(String.class)))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(NoSuchElementException.class,
            () -> outboxStatusService.markSent("UUID"));
  }

  @Test
  void shouldThrowExceptionWhenEventNotFoundWhenFailure() {
    when(outboxEventRepository.findById(any(String.class)))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(NoSuchElementException.class,
            () -> outboxStatusService.markFailedAttempt("UUID"));
  }
}
