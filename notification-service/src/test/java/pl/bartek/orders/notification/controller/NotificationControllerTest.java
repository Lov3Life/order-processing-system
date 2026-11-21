package pl.bartek.orders.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.notification.dto.NotificationDTO;
import pl.bartek.orders.notification.exception.NotificationNotFoundException;
import pl.bartek.orders.notification.service.NotificationService;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
public class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private NotificationService notificationService;

  @Test
  void shouldReturnAllNotifications() throws Exception {
    Instant now = Instant.now();
    NotificationDTO notificationDTO1 = new NotificationDTO(
            1L, 1L, ProcessingStatus.ACCEPTED, "accepted", now);
    NotificationDTO notificationDTO2 = new NotificationDTO(
            2L, 2L, ProcessingStatus.REJECTED, "rejected", now);
    when(notificationService.getAllNotifications(null))
            .thenReturn(List.of(notificationDTO1, notificationDTO2));
    mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].orderId").value(1L))
            .andExpect(jsonPath("$[0].status").value(ProcessingStatus.ACCEPTED.toString()))
            .andExpect(jsonPath("$[0].message").value("accepted"))
            .andExpect(jsonPath("$[0].createdAt").value(now.toString()))
            .andExpect(jsonPath("$[1].id").value(2L))
            .andExpect(jsonPath("$[1].orderId").value(2L))
            .andExpect(jsonPath("$[1].status").value(ProcessingStatus.REJECTED.toString()))
            .andExpect(jsonPath("$[1].message").value("rejected"))
            .andExpect(jsonPath("$[1].createdAt").value(now.toString()));
    verify(notificationService).getAllNotifications(null);
  }

  @Test
  void shouldReturnNotificationsForOrderId() throws Exception {
    Instant now = Instant.now();
    NotificationDTO notificationDTO = new NotificationDTO(
            1L, 1L, ProcessingStatus.ACCEPTED, "accepted", now);
    when(notificationService.getAllNotifications(1L))
            .thenReturn(List.of(notificationDTO));
    mockMvc.perform(get("/api/notifications")
                    .param("orderId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].orderId").value(1L))
            .andExpect(jsonPath("$[0].status").value(ProcessingStatus.ACCEPTED.toString()))
            .andExpect(jsonPath("$[0].message").value("accepted"))
            .andExpect(jsonPath("$[0].createdAt").value(now.toString()));
    verify(notificationService).getAllNotifications(1L);
  }

  @Test
  void shouldReturnEmptyListAllNotifications() throws Exception {
    when(notificationService.getAllNotifications(null))
            .thenReturn(List.of());
    mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    verify(notificationService).getAllNotifications(null);
  }

  @Test
  void shouldReturnEmptyListNotificationFilterOrderId() throws Exception {
    when(notificationService.getAllNotifications(1L))
            .thenReturn(List.of());
    mockMvc.perform(get("/api/notifications")
                    .param("orderId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    verify(notificationService).getAllNotifications(1L);
  }

  @Test
  void shouldReturnNotification() throws Exception {
    Instant now = Instant.now();
    NotificationDTO notificationDTO = new NotificationDTO(
            1L, 1L, ProcessingStatus.ACCEPTED, "accepted", now);
    when(notificationService.getNotification(1L))
      .thenReturn(notificationDTO);
    mockMvc.perform(get("/api/notifications/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.orderId").value(1L))
            .andExpect(jsonPath("$.status").value(ProcessingStatus.ACCEPTED.toString()))
            .andExpect(jsonPath("$.message").value("accepted"))
            .andExpect(jsonPath("$.createdAt").value(now.toString()));
    verify(notificationService).getNotification(1L);
  }

  @Test
  void should404ThrowNotificationNotFoundExceptionWhenNotificationNotFound() throws Exception {
    when(notificationService.getNotification(1L))
            .thenThrow(new NotificationNotFoundException(1L));
    mockMvc.perform(get("/api/notifications/1"))
            .andExpect(status().isNotFound());
    verify(notificationService).getNotification(1L);
  }
}
