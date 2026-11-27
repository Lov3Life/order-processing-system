package pl.bartek.orders.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.notification.NotificationServiceApplication;
import pl.bartek.orders.notification.dto.NotificationDTO;
import pl.bartek.orders.order.OrderServiceApplication;
import pl.bartek.orders.order.dto.*;
import pl.bartek.orders.order.enums.OrderStatus;
import pl.bartek.orders.processing.ProcessingServiceApplication;
import pl.bartek.orders.processing.dto.ProductCreateRequest;
import pl.bartek.orders.processing.dto.ProductDTO;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;

@Testcontainers
public class OrderWorkflowE2EIT {

  @Container
  static final MySQLContainer mysql = new MySQLContainer(
          DockerImageName.parse("mysql:8.4.13"))
          .withDatabaseName("e2e_bootstrap")
          .withUsername("test")
          .withPassword("test");

  @Container
  static final KafkaContainer kafka = new KafkaContainer(
          DockerImageName.parse("apache/kafka:4.3.2"));

  private static ConfigurableApplicationContext orderContext;
  private static ConfigurableApplicationContext processingContext;
  private static ConfigurableApplicationContext notificationContext;

  private static final String ORDERS_DB = "order-processing-system-orders_db";

  private static final String INVENTORY_DB = "order-processing-system-inventory_db";

  private static final String NOTIFICATIONS_DB = "order-processing-system-notifications_db";

  private static final HttpClient httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  private static JsonMapper jsonMapper;

  private static String baseUrl(ConfigurableApplicationContext context) {
    WebServerApplicationContext webContext = (WebServerApplicationContext) context;
    int port = webContext.getWebServer().getPort();
    return "http://localhost:" + port;
  }

  private static <T> T postJson(ConfigurableApplicationContext context, String path,
                                Object body, Class<T> responseType) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl(context) + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    Assertions.assertEquals(201, response.statusCode(), "POST " + path + ": " + response.body());
    return jsonMapper.readValue(response.body(), responseType);
  }

  private static <T> T getJson(ConfigurableApplicationContext context,
                               String path, Class<T> responseType) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl(context) + path))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    Assertions.assertEquals(200, response.statusCode(), "GET " + path + ": " + response.body());
    return jsonMapper.readValue(response.body(), responseType);
  }

  private static void assertSentOutboxEvent(ConfigurableApplicationContext context,
                                            Long orderId, String topic) throws Exception {
    DataSource dataSource = context.getBean(DataSource.class);
    String sql = """
          SELECT status, published_at
          FROM outbox_events
          WHERE aggregate_id = ?
            AND topic = ?
          """;
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, orderId);
      statement.setString(2, topic);
      try (ResultSet result = statement.executeQuery()) {
        Assertions.assertTrue(result.next(), "Missing outbox event for topic: " + topic);
        Assertions.assertEquals("SENT", result.getString("status"));
        Assertions.assertNotNull(result.getTimestamp("published_at"));
        Assertions.assertFalse(result.next(), "Duplicate outbox event for topic: " + topic);
      }
    }
  }

  @BeforeAll
  static void startSystem() throws Exception {
    initializeDatabases();
    try {
      notificationContext = startApplication(NotificationServiceApplication.class,
              "notification-service", NOTIFICATIONS_DB, "OrderFinalizedEvent");
      processingContext = startApplication(ProcessingServiceApplication.class,
              "processing-service", INVENTORY_DB, "OrderCreatedEvent");
      orderContext = startApplication(OrderServiceApplication.class,
              "order-service", ORDERS_DB, "OrderProcessedEvent");
      jsonMapper = orderContext.getBean(JsonMapper.class);
    } catch (RuntimeException exception) {
      stopSystem();
      throw exception;
    }
  }

  private static void initializeDatabases() throws Exception {
    List<String> scripts = List.of(
            "database/01-orders_db.sql",
            "database/02-inventory_db.sql",
            "database/03-notifications_db.sql");
    try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword())) {
      for (String script : scripts) {
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(script)));
      }
      try (Statement statement = connection.createStatement()) {
        for (String database : List.of(ORDERS_DB, INVENTORY_DB, NOTIFICATIONS_DB)) {
          statement.execute("GRANT ALL PRIVILEGES ON `" + database + "`.* TO 'test'@'%'");
        }
      }
    }
  }

  private static ConfigurableApplicationContext startApplication(
          Class<?> applicationClass, String applicationName, String databaseName, String eventType) {
    String jdbcUrl = "jdbc:mysql://"
                     + mysql.getHost()
                     + ":"
                     + mysql.getMappedPort(3306)
                     + "/"
                     + databaseName
                     + "?connectionTimeZone=UTC";
    return new SpringApplicationBuilder(applicationClass).run(
            "--spring.config.location=classpath:/e2e/application.properties",
            "--spring.application.name=" + applicationName,
            "--spring.datasource.url=" + jdbcUrl,
            "--spring.datasource.username=test",
            "--spring.datasource.password=test",
            "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
            "--spring.kafka.consumer.group-id=e2e-" + applicationName,
            "--spring.kafka.consumer.properties[spring.json.value.default.type]="
            + "pl.bartek.orders.event." + eventType
    );
  }

  private static ProductDTO createProduct(String name, int stockQuantity) throws Exception {
    ProductDTO product = postJson(processingContext, "/api/products",
            new ProductCreateRequest(name + UUID.randomUUID(), stockQuantity), ProductDTO.class);
    Assertions.assertNotNull(product.id());
    Assertions.assertEquals(stockQuantity, product.stockQuantity());
    return product;
  }

  private static CustomerDTO createCustomer() throws Exception {
    CustomerDTO customer = postJson(orderContext, "/api/customers",
            new CustomerCreateRequest("Matt", "Murdock",
                    "e2e-" + UUID.randomUUID() + "@example.com"), CustomerDTO.class);
    Assertions.assertNotNull(customer.id());
    return customer;
  }

  private static OrderDTO createOrder(Long customerId, List<OrderItemCreateRequest> items) throws Exception {
    OrderDTO order = postJson(orderContext, "/api/orders", new OrderCreateRequest(customerId, items), OrderDTO.class);
    Assertions.assertNotNull(order.id());
    Assertions.assertEquals(OrderStatus.PENDING, order.orderStatus());
    return order;
  }

  private static void assertNotification(Long orderId, ProcessingStatus expectedStatus, String expectedMessage) throws Exception {
    NotificationDTO[] notifications = getJson(notificationContext, "/api/notifications?orderId=" + orderId, NotificationDTO[].class);
    Assertions.assertEquals(1, notifications.length);
    NotificationDTO notification = notifications[0];
    Assertions.assertEquals(orderId, notification.orderId());
    Assertions.assertEquals(expectedStatus, notification.status());
    Assertions.assertEquals(expectedMessage, notification.message());
    Assertions.assertNotNull(notification.createdAt());
  }

  private static void assertAllOutboxEventsSent(Long orderId) throws Exception {
    assertSentOutboxEvent(orderContext, orderId, "orders.created");
    assertSentOutboxEvent(processingContext, orderId, "orders.processed");
    assertSentOutboxEvent(orderContext, orderId, "orders.finalized");
  }

  private static void awaitFinalizedWorkflow(Long orderId, ProcessingStatus expectedStatus, String expectedMessage) {
    await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(300))
            .untilAsserted(() -> {
              OrderDTO order = getJson(orderContext, "/api/orders/" + orderId, OrderDTO.class);
              Assertions.assertEquals(OrderStatus.valueOf(expectedStatus.name()), order.orderStatus());
              assertNotification(orderId, expectedStatus, expectedMessage);
              assertAllOutboxEventsSent(orderId);
            });
  }

  @AfterAll
  static void stopSystem() {
    if (orderContext != null)
      orderContext.close();
    if (processingContext != null)
      processingContext.close();
    if (notificationContext != null)
      notificationContext.close();
  }

  @Test
  void shouldStartAllServices() {
    Assertions.assertTrue(orderContext.isActive());
    Assertions.assertTrue(processingContext.isActive());
    Assertions.assertTrue(notificationContext.isActive());
  }

  @Test
  void shouldCompleteAcceptedOrderWorkflow() throws Exception {
    ProductDTO product = createProduct("E2E Monitor", 10);
    CustomerDTO customer = createCustomer();
    OrderDTO order = createOrder(customer.id(), List.of(new OrderItemCreateRequest(product.id(), 3)));
    awaitFinalizedWorkflow(order.id(), ProcessingStatus.ACCEPTED, "Order " + order.id() + " has been accepted.");
    ProductDTO updatedProduct = getJson(processingContext, "/api/products/" + product.id(), ProductDTO.class);
    Assertions.assertEquals(7, updatedProduct.stockQuantity());
  }

  @Test
  void shouldCompleteRejectedOrderWorkflow() throws Exception {
    ProductDTO product = createProduct("E2E Monitor", 2);
    CustomerDTO customer = createCustomer();
    OrderDTO order = createOrder(customer.id(), List.of(new OrderItemCreateRequest(product.id(), 5)));
    String reason = "Insufficient quantity(5) of the product with ID: " + product.id();
    String expectedMessage = "Order " + order.id() + " has been rejected. Reason: " + reason;
    awaitFinalizedWorkflow(order.id(), ProcessingStatus.REJECTED, expectedMessage);
    ProductDTO updatedProduct = getJson(processingContext, "/api/products/" + product.id(), ProductDTO.class);
    Assertions.assertEquals(2, updatedProduct.stockQuantity());
  }

  @Test
  void shouldRejectOrderWithMissingProduct() throws Exception {
    ProductDTO existingProduct = createProduct("E2E Monitor", 10);
    CustomerDTO customer = createCustomer();
    Long missingProductId = Long.MAX_VALUE;
    OrderDTO order = createOrder(customer.id(), List.of(new OrderItemCreateRequest(missingProductId, 3)));
    String reason = "product with id " + missingProductId + " not found";
    String expectedMessage = "Order " + order.id() + " has been rejected. Reason: " + reason;
    awaitFinalizedWorkflow(order.id(), ProcessingStatus.REJECTED, expectedMessage);
    ProductDTO updatedProduct = getJson(processingContext, "/api/products/" + existingProduct.id(), ProductDTO.class);
    Assertions.assertEquals(10, updatedProduct.stockQuantity());
  }

  @Test
  void shouldNotPartiallyReserveProducts() throws Exception {
    ProductDTO monitor = createProduct("E2E Monitor", 10);
    ProductDTO keyboard = createProduct("E2E Keyboard", 2);
    CustomerDTO customer = createCustomer();
    OrderDTO order = createOrder(customer.id(), List.of(
                    new OrderItemCreateRequest(monitor.id(), 3),
                    new OrderItemCreateRequest(keyboard.id(), 5)));
    String reason = "Insufficient quantity(5) of the product with ID: " + keyboard.id();
    String expectedMessage = "Order " + order.id() + " has been rejected. Reason: " + reason;
    awaitFinalizedWorkflow(order.id(), ProcessingStatus.REJECTED, expectedMessage);
    ProductDTO updatedMonitor = getJson(processingContext, "/api/products/" + monitor.id(), ProductDTO.class);
    ProductDTO updatedKeyboard = getJson(processingContext, "/api/products/" + keyboard.id(), ProductDTO.class);
    Assertions.assertEquals(10, updatedMonitor.stockQuantity());
    Assertions.assertEquals(2, updatedKeyboard.stockQuantity());
  }

  @Test
  void shouldCompleteMultiItemOrderWorkflow() throws Exception {
    ProductDTO monitor = createProduct("E2E Monitor", 10);
    ProductDTO keyboard = createProduct("E2E Keyboard", 15);
    CustomerDTO customer = createCustomer();
    OrderDTO order = createOrder(customer.id(), List.of(
                    new OrderItemCreateRequest(monitor.id(), 3),
                    new OrderItemCreateRequest(keyboard.id(), 5)));
    awaitFinalizedWorkflow(order.id(), ProcessingStatus.ACCEPTED, "Order " + order.id() + " has been accepted.");
    ProductDTO updatedMonitor = getJson(processingContext, "/api/products/" + monitor.id(), ProductDTO.class);
    ProductDTO updatedKeyboard = getJson(processingContext, "/api/products/" + keyboard.id(), ProductDTO.class);
    Assertions.assertEquals(7, updatedMonitor.stockQuantity());
    Assertions.assertEquals(10, updatedKeyboard.stockQuantity());
    OrderDTO updatedOrder = getJson(orderContext, "/api/orders/" + order.id(), OrderDTO.class);
    Assertions.assertEquals(2, updatedOrder.items().size());
    Assertions.assertTrue(updatedOrder.items().stream()
            .anyMatch(item -> item.productId().equals(monitor.id()) && item.quantity() == 3));
    Assertions.assertTrue(updatedOrder.items().stream()
            .anyMatch(item -> item.productId().equals(keyboard.id()) && item.quantity() == 5));
  }

  @Test
  void shouldHandleConcurrentOrdersWithoutOverselling() throws Exception {
    ProductDTO product = createProduct("E2E Monitor", 1);
    CustomerDTO firstCustomer = createCustomer();
    CustomerDTO secondCustomer = createCustomer();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    OrderDTO firstOrder;
    OrderDTO secondOrder;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<OrderDTO> firstFuture = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS))
          throw new AssertionError("Start timeout");
        return createOrder(firstCustomer.id(), List.of(new OrderItemCreateRequest(product.id(), 1)));
      });
      Future<OrderDTO> secondFuture = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS))
          throw new AssertionError("Start timeout");
        return createOrder(secondCustomer.id(), List.of(new OrderItemCreateRequest(product.id(), 1)));
      });
      Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      firstOrder = firstFuture.get(30, TimeUnit.SECONDS);
      secondOrder = secondFuture.get(30, TimeUnit.SECONDS);
    } finally {
      start.countDown();
    }
    await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(300))
            .untilAsserted(() -> {
              OrderDTO first = getJson(orderContext, "/api/orders/" + firstOrder.id(), OrderDTO.class);
              OrderDTO second = getJson(orderContext, "/api/orders/" + secondOrder.id(), OrderDTO.class);
              OrderStatus firstStatus = first.orderStatus();
              OrderStatus secondStatus = second.orderStatus();
              Assertions.assertTrue(firstStatus == OrderStatus.ACCEPTED || firstStatus == OrderStatus.REJECTED);
              Assertions.assertTrue(secondStatus == OrderStatus.ACCEPTED || secondStatus == OrderStatus.REJECTED);
              Assertions.assertNotEquals(firstStatus, secondStatus);
              String rejectedReason = "Insufficient quantity(1) of the product with ID: " + product.id();
              for (OrderDTO current : List.of(first, second)) {
                ProcessingStatus status = ProcessingStatus.valueOf(current.orderStatus().name());
                String message;
                if (status == ProcessingStatus.ACCEPTED) {
                  message = "Order " + current.id() + " has been accepted.";
                } else {
                  message = "Order " + current.id() + " has been rejected. Reason: " + rejectedReason;
                }
                assertNotification(current.id(), status, message);
                assertAllOutboxEventsSent(current.id());
              }
              ProductDTO updatedProduct = getJson(processingContext, "/api/products/" + product.id(), ProductDTO.class);
              Assertions.assertEquals(0, updatedProduct.stockQuantity());
            });
  }
}