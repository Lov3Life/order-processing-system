package pl.bartek.orders.notification.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.notification.dto.NotificationDTO;
import pl.bartek.orders.notification.entity.Notification;
import pl.bartek.orders.notification.exception.NotificationInvalidDataException;
import pl.bartek.orders.notification.exception.NotificationNotFoundException;
import pl.bartek.orders.notification.repository.NotificationRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

  @Mock
  private  NotificationRepository notificationRepository;

  private NotificationService notificationService;

  @BeforeEach
  void setup() {
    notificationService = new NotificationService(notificationRepository);
  }

  private static Stream<Arguments> statusAndMessageReturn(){
    return Stream.of(
            Arguments.of(ProcessingStatus.ACCEPTED, null, "Order 1 has been accepted."),
            Arguments.of(ProcessingStatus.REJECTED, "reason", "Order 1 has been rejected. Reason: reason")
    );
  }

  private static Stream<Arguments> statusAndMessageException(){
    return Stream.of(
            Arguments.of(ProcessingStatus.ACCEPTED, null,
                    ProcessingStatus.REJECTED, "Order 1 has been rejected. Reason: reason"),
            Arguments.of(ProcessingStatus.REJECTED, "reasonnn",
                    ProcessingStatus.REJECTED, "Order 1 has been rejected. Reason: reason")
    );
  }


  @Test
  void shouldReturnAllNotifications() {
    Notification notification1 = new Notification(1L, ProcessingStatus.ACCEPTED, null);
    Notification notification2 = new Notification(2L, ProcessingStatus.REJECTED, "rejected");
    when(notificationRepository.findAll())
            .thenReturn(Arrays.asList(notification1, notification2));
    List<NotificationDTO> response = notificationService.getAllNotifications(null);
    Assertions.assertEquals(2, response.size());
    Assertions.assertEquals(notification1.getOrderId(), response.getFirst().orderId());
    Assertions.assertEquals(notification1.getStatus(), response.getFirst().status());
    Assertions.assertEquals(notification1.getMessage(), response.getFirst().message());
    Assertions.assertEquals(notification2.getOrderId(), response.getLast().orderId());
    Assertions.assertEquals(notification2.getStatus(), response.getLast().status());
    Assertions.assertEquals(notification2.getMessage(), response.getLast().message());
    verify(notificationRepository).findAll();
    verify(notificationRepository, never()).findByOrderId(anyLong());
  }

  @Test
  void shouldReturnAllNotificationsEmptyList() {
    when(notificationRepository.findAll())
            .thenReturn(List.of());
    List<NotificationDTO> response = notificationService.getAllNotifications(null);
    Assertions.assertEquals(0, response.size());
    verify(notificationRepository).findAll();
    verify(notificationRepository, never()).findByOrderId(anyLong());
  }

  @Test
  void shouldReturnNotificationByOrderId() {
    Notification notification = new Notification(1L, ProcessingStatus.ACCEPTED, null);
    when(notificationRepository.findByOrderId(1L))
            .thenReturn(Optional.of(notification));
    List<NotificationDTO> response = notificationService.getAllNotifications(1L);
    Assertions.assertEquals(1, response.size());
    Assertions.assertEquals(notification.getOrderId(), response.getFirst().orderId());
    Assertions.assertEquals(notification.getStatus(), response.getFirst().status());
    Assertions.assertEquals(notification.getMessage(), response.getFirst().message());
    verify(notificationRepository, never()).findAll();
    verify(notificationRepository).findByOrderId(1L);
  }

  @Test
  void shouldReturnAllNotificationByOrderIdEmptyList() {
    when(notificationRepository.findByOrderId(1L))
            .thenReturn(Optional.empty());
    List<NotificationDTO> response = notificationService.getAllNotifications(1L);
    Assertions.assertEquals(0, response.size());
    verify(notificationRepository, never()).findAll();
    verify(notificationRepository).findByOrderId(1L);
  }

  @Test
  void shouldReturnNotificationById(){
    Notification notification = new Notification(10L, ProcessingStatus.ACCEPTED, null);
    ReflectionTestUtils.setField(notification, "id", 1L);
    when(notificationRepository.findById(1L))
            .thenReturn(Optional.of(notification));
    NotificationDTO response = notificationService.getNotification(1L);
    Assertions.assertEquals(1L, response.id());
    Assertions.assertEquals(notification.getOrderId(), response.orderId());
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, response.status());
    Assertions.assertEquals(notification.getMessage(), response.message());
    verify(notificationRepository).findById(1L);
  }

  @Test
  void shouldThrowNotificationNotFoundException(){
    when(notificationRepository.findById(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(NotificationNotFoundException.class,
            () -> notificationService.getNotification(1L));
    verify(notificationRepository).findById(1L);
  }

  @Test
  void shouldCreateNotificationWithAcceptedStatus(){
    when(notificationRepository.findByOrderId(1L))
            .thenReturn(Optional.empty());
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    verify(notificationRepository).save(argThat(notification ->
            notification.getOrderId() == 1L &&
            notification.getStatus() == ProcessingStatus.ACCEPTED &&
            notification.getMessage().equals("Order 1 has been accepted.")
    ));
  }

  @Test
  void shouldCreateNotificationWithRejectedStatus(){
    when(notificationRepository.findByOrderId(1L))
            .thenReturn(Optional.empty());
    notificationService.createNotification(1L, ProcessingStatus.REJECTED, "reason");
    verify(notificationRepository).save(argThat(notification ->
            notification.getOrderId() == 1L &&
            notification.getStatus() == ProcessingStatus.REJECTED &&
            notification.getMessage().equals("Order 1 has been rejected. Reason: reason")
    ));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {-1L, 0L})
  void shouldThrowNotificationInvalidDataExceptionWhenIdIsInvalid(Long orderId) {
    Assertions.assertThrows(NotificationInvalidDataException.class,
            () -> notificationService.createNotification(orderId, ProcessingStatus.ACCEPTED, null));
    verifyNoInteractions(notificationRepository);
  }

  @Test
  void shouldThrowNotificationInvalidDataExceptionWhenStatusIsNull() {
    Assertions.assertThrows(NotificationInvalidDataException.class,
            () -> notificationService.createNotification(1L, null, null));
    verifyNoInteractions(notificationRepository);
  }

  @Test
  void shouldThrowNotificationInvalidDataExceptionWhenMessageIsTooLong() {
    String reason = "a".repeat(501);
    Assertions.assertThrows(NotificationInvalidDataException.class,
            () -> notificationService.createNotification(1L, null, reason));
    verifyNoInteractions(notificationRepository);
  }

  @ParameterizedTest
  @MethodSource("statusAndMessageReturn")
  void shouldReturnWithoutSavingWhenStatusInRepositoryIsSameLikeWanted(ProcessingStatus status, String reason, String message) {
    when(notificationRepository.findByOrderId(1L))
            .thenReturn(Optional.of(new Notification(1L, status, message)));
    notificationService.createNotification(1L, status, reason);
    verify(notificationRepository).findByOrderId(1L);
    verify(notificationRepository, never()).save(any());
  }

  @ParameterizedTest
  @MethodSource("statusAndMessageException")
  void shouldThrowNotificationInvalidDataExceptionWhenStatusOrMessageIsDifferent(
          ProcessingStatus statusRepo, String messageRepo, ProcessingStatus statusNew, String reasonNew) {
    when(notificationRepository.findByOrderId(1L))
            .thenReturn(Optional.of(new Notification(1L, statusRepo, messageRepo)));
    Assertions.assertThrows(NotificationInvalidDataException.class,
            () -> notificationService.createNotification(1L, statusNew, reasonNew));
    verify(notificationRepository).findByOrderId(1L);
    verify(notificationRepository, never()).save(any());
  }
}
