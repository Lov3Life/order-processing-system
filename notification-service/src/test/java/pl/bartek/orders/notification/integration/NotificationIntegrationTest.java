package pl.bartek.orders.notification.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.bartek.orders.event.OrderFinalizedEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.notification.entity.Notification;
import pl.bartek.orders.notification.exception.NotificationInvalidDataException;
import pl.bartek.orders.notification.repository.NotificationRepository;
import pl.bartek.orders.notification.service.NotificationService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class NotificationIntegrationTest {

  @Container
  @ServiceConnection
  static final MySQLContainer mysql = new MySQLContainer(
          DockerImageName.parse("mysql:8.4.13"))
          .withDatabaseName("order-processing-system-notifications_db")
          .withUsername("test")
          .withPassword("test")
          .withInitScript("database/03-notifications_db.sql");

  @Container
  @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer(
          DockerImageName.parse("apache/kafka:4.3.2"));

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    notificationRepository.deleteAll();
  }

  private static final AtomicLong ORDER_IDS = new AtomicLong(System.currentTimeMillis());

  @Test
  void shouldPersistAcceptedNotification() {
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    List<Notification> notifications = notificationRepository.findAll();
    Assertions.assertEquals(1, notifications.size());
    Notification notification = notifications.getFirst();
    Assertions.assertNotNull(notification.getId());
    Assertions.assertEquals(1L, notification.getOrderId());
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, notification.getStatus());
    Assertions.assertNotNull(notification.getCreatedAt());
    Assertions.assertEquals("Order 1 has been accepted.", notification.getMessage());
  }

  @Test
  void shouldPersistRejectedNotification() {
    notificationService.createNotification(1L, ProcessingStatus.REJECTED, "reason");
    List<Notification> notifications = notificationRepository.findAll();
    Assertions.assertEquals(1, notifications.size());
    Notification notification = notifications.getFirst();
    Assertions.assertNotNull(notification.getId());
    Assertions.assertEquals(1L, notification.getOrderId());
    Assertions.assertEquals(ProcessingStatus.REJECTED, notification.getStatus());
    Assertions.assertNotNull(notification.getCreatedAt());
    Assertions.assertEquals("Order 1 has been rejected. Reason: reason", notification.getMessage());
  }

  @Test
  void shouldReturnPersistedNotifications() throws Exception {
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    notificationService.createNotification(2L, ProcessingStatus.REJECTED, "reason");
    mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[1].id").isNumber())
            .andExpect(jsonPath("$[*].message", containsInAnyOrder(
                    "Order 1 has been accepted.",
                    "Order 2 has been rejected. Reason: reason")));
  }

  @Test
  void shouldFilterNotificationsByOrderId() throws Exception {
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    notificationService.createNotification(2L, ProcessingStatus.REJECTED, "reason");
    mockMvc.perform(get("/api/notifications")
                    .param("orderId", String.valueOf(1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].orderId").value(1))
            .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
            .andExpect(jsonPath("$[0].message", not(containsString("Reason"))));
  }

  @Test
  void shouldReturnNotificationById() throws Exception {
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    Long notificationId = notificationRepository.findAll().getFirst().getId();
    mockMvc.perform(get("/api/notifications/{id}", notificationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(notificationId))
            .andExpect(jsonPath("$.orderId").value(1))
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.message").value("Order 1 has been accepted."));
  }

  @Test
  void shouldIgnoreDuplicateNotification() {
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    List<Notification> notifications = notificationRepository.findAll();
    Assertions.assertEquals(1, notifications.size());
  }

  @Test
  void shouldRejectConflictingNotification() {
    notificationService.createNotification(1L, ProcessingStatus.ACCEPTED, null);
    Assertions.assertThrows(NotificationInvalidDataException.class,
            () -> notificationService.createNotification(1L, ProcessingStatus.REJECTED, "reason"));
    List<Notification> notifications = notificationRepository.findAll();
    Assertions.assertEquals(1, notifications.size());
    Notification notification = notifications.getFirst();
    Assertions.assertNotNull(notification.getId());
    Assertions.assertEquals(1L, notification.getOrderId());
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, notification.getStatus());
    Assertions.assertEquals("Order 1 has been accepted.", notification.getMessage());
  }

  @Test
  void shouldConsumeOrderFinalizedEvent() throws Exception {
    Long orderId = ORDER_IDS.incrementAndGet();
    OrderFinalizedEvent event = new OrderFinalizedEvent(
            orderId, ProcessingStatus.REJECTED, "Insufficient stock", Instant.now());
    kafkaTemplate.send("orders.finalized", orderId.toString(), jsonMapper.writeValueAsString(event))
            .get(10, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> {
              Notification notification = notificationRepository.findByOrderId(orderId)
                      .orElseThrow();
              Assertions.assertNotNull(notification.getId());
              Assertions.assertEquals(orderId, notification.getOrderId());
              Assertions.assertEquals(ProcessingStatus.REJECTED, notification.getStatus());
              Assertions.assertEquals(
                      "Order " + orderId + " has been rejected. Reason: Insufficient stock", notification.getMessage());
              Assertions.assertNotNull(notification.getCreatedAt());
              Assertions.assertEquals(1, notificationRepository.count());
            });
  }

  @Test
  void shouldIgnoreDuplicateKafkaEvent() throws Exception {
    Long orderId = ORDER_IDS.incrementAndGet();
    Long markerOrderId = ORDER_IDS.incrementAndGet();
    OrderFinalizedEvent event = new OrderFinalizedEvent(
            orderId, ProcessingStatus.ACCEPTED, null, Instant.now());
    OrderFinalizedEvent marker = new OrderFinalizedEvent(
            markerOrderId, ProcessingStatus.ACCEPTED, null, Instant.now());
    String payload = jsonMapper.writeValueAsString(event);
    kafkaTemplate.send("orders.finalized", 0, orderId.toString(), payload)
            .get(10, TimeUnit.SECONDS);
    kafkaTemplate.send("orders.finalized", 0, orderId.toString(), payload)
            .get(10, TimeUnit.SECONDS);
    kafkaTemplate.send("orders.finalized", 0, markerOrderId.toString(), jsonMapper.writeValueAsString(marker))
            .get(10, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> {
              Notification markerNotification = notificationRepository.findByOrderId(markerOrderId)
                      .orElseThrow();
              Assertions.assertEquals(ProcessingStatus.ACCEPTED, markerNotification.getStatus());
            });
    Notification notification = notificationRepository.findByOrderId(orderId)
            .orElseThrow();
    Assertions.assertNotNull(notification.getId());
    Assertions.assertEquals(orderId, notification.getOrderId());
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, notification.getStatus());
    Assertions.assertEquals("Order " + orderId + " has been accepted.", notification.getMessage());
    Assertions.assertEquals(2, notificationRepository.count());
  }
}
