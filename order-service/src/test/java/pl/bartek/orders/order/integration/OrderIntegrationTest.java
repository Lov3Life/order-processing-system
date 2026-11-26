package pl.bartek.orders.order.integration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import pl.bartek.orders.event.*;
import pl.bartek.orders.order.dto.OrderCreateRequest;
import pl.bartek.orders.order.dto.OrderDTO;
import pl.bartek.orders.order.dto.OrderItemCreateRequest;
import pl.bartek.orders.order.entity.Customer;
import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.entity.OrderItem;
import pl.bartek.orders.order.enums.OrderStatus;
import pl.bartek.orders.order.exception.OrderAlreadyProcessedException;
import pl.bartek.orders.order.repository.CustomerRepository;
import pl.bartek.orders.order.repository.OrderItemRepository;
import pl.bartek.orders.order.repository.OrderRepository;
import pl.bartek.orders.order.service.OrderService;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.enums.OutboxStatus;
import pl.bartek.orders.outbox.publisher.OutboxPublisher;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.outbox.service.OutboxService;
import pl.bartek.orders.outbox.service.OutboxStatusService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class OrderIntegrationTest {

  @Container
  @ServiceConnection
  static final MySQLContainer mysql = new MySQLContainer(DockerImageName
          .parse("mysql:8.4.13"))
          .withDatabaseName("order-processing-system-orders_db")
          .withUsername("test")
          .withPassword("test")
          .withInitScript("database/01-orders_db.sql");

  @Container
  @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer(DockerImageName
          .parse("apache/kafka:4.3.2"));

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderItemRepository orderItemRepository;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private OrderService orderService;

  @Autowired
  private JsonMapper jsonMapper;

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired
  private OutboxStatusService outboxStatusService;

  @MockitoBean
  private OutboxPublisher outboxPublisher;

  @MockitoSpyBean
  private OutboxService outboxService;

  @BeforeEach
  void setUp() {
    orderItemRepository.deleteAll();
    orderRepository.deleteAll();
    customerRepository.deleteAll();
    outboxEventRepository.deleteAll();
  }

  private Customer saveCustomer(String email) {
    return customerRepository.save(
            new Customer("Matt", "Murdock", email));
  }

  private Order savePendingOrder(Customer customer) {
    Order order = new Order();
    order.setCustomer(customer);
    order.setOrderStatus(OrderStatus.PENDING);
    order.setOrderDate(Instant.now());
    OrderItem item = new OrderItem();
    item.setProductId(10L);
    item.setQuantity(2);
    order.addOrderItem(item);
    return orderRepository.saveAndFlush(order);
  }

  private void publishOutboxEvents() {
    OutboxPublisher publisher = new OutboxPublisher(outboxEventRepository, outboxStatusService, kafkaTemplate);
    publisher.publishPendingEvents();
  }

  private KafkaConsumer<String, String> createKafkaConsumer(String topic) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
    consumer.subscribe(List.of(topic));
    return consumer;
  }

  private ConsumerRecord<String, String> awaitKafkaRecord(KafkaConsumer<String, String> consumer,
                                                          String topic,
                                                          Long orderId) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        if (record.topic().equals(topic) && orderId.toString().equals(record.key())) {
          return record;
        }
      }
    }
    throw new AssertionError("Kafka record not received for order: " + orderId);
  }

  @Test
  void shouldCreateCustomer() throws Exception {
    String request = """
            {
              "firstName": "Matt",
              "lastName": "Murdock",
              "email": "matt.murdock@example.com"
            }
            """;
    mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.firstName").value("Matt"))
            .andExpect(jsonPath("$.lastName").value("Murdock"))
            .andExpect(jsonPath("$.email").value("matt.murdock@example.com"));
    List<Customer> customers = customerRepository.findAll();
    Assertions.assertEquals(1, customers.size());
    Customer customer = customers.getFirst();
    Assertions.assertNotNull(customer.getId());
    Assertions.assertEquals("Matt", customer.getFirstName());
    Assertions.assertEquals("Murdock", customer.getLastName());
    Assertions.assertEquals("matt.murdock@example.com", customer.getEmail());
    Assertions.assertEquals(0, orderRepository.count());
    Assertions.assertEquals(0, outboxEventRepository.count());
  }

  @Test
  void shouldCreateOrderAndPersistItemsAndOutboxEvent() throws Exception {
    Customer customer = saveCustomer("matt.murdock@example.com");
    Long customerId = customer.getId();
    String request = """
            {
              "customerId": %d,
              "items": [
                {
                  "productId": 10,
                  "quantity": 2
                },
                {
                  "productId": 15,
                  "quantity": 3
                }
              ]
            }
            """.formatted(customerId);
    mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.orderStatus").value("PENDING"))
            .andExpect(jsonPath("$.items", hasSize(2)));
    List<Order> orders = orderRepository.findAll();
    Assertions.assertEquals(1, orders.size());
    Order order = orders.getFirst();
    Assertions.assertNotNull(order.getId());
    Assertions.assertEquals(customerId, order.getCustomer().getId());
    Assertions.assertEquals(OrderStatus.PENDING, order.getOrderStatus());
    Assertions.assertNotNull(order.getOrderDate());
    List<OrderItem> items = orderItemRepository.findAll();
    Assertions.assertEquals(2, items.size());
    Map<Long, OrderItem> itemsByProductId = items.stream()
            .collect(Collectors.toMap(
                    OrderItem::getProductId,
                    Function.identity()));
    Assertions.assertTrue(itemsByProductId.containsKey(10L));
    Assertions.assertTrue(itemsByProductId.containsKey(15L));
    OrderItem firstItem = itemsByProductId.get(10L);
    OrderItem secondItem = itemsByProductId.get(15L);
    Assertions.assertNotNull(firstItem.getId());
    Assertions.assertEquals(2, firstItem.getQuantity());
    Assertions.assertEquals(order.getId(), firstItem.getOrder().getId());
    Assertions.assertNotNull(secondItem.getId());
    Assertions.assertEquals(3, secondItem.getQuantity());
    Assertions.assertEquals(order.getId(), secondItem.getOrder().getId());
    List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
    Assertions.assertEquals(1, outboxEvents.size());
    OutboxEvent outboxEvent = outboxEvents.getFirst();
    Assertions.assertNotNull(outboxEvent.getId());
    Assertions.assertEquals(order.getId(), outboxEvent.getAggregateId());
    Assertions.assertEquals("OrderCreatedEvent", outboxEvent.getEventType());
    Assertions.assertEquals("orders.created", outboxEvent.getTopic());
    Assertions.assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
    Assertions.assertEquals(0, outboxEvent.getAttempts());
    Assertions.assertNotNull(outboxEvent.getCreatedAt());
    Assertions.assertNotNull(outboxEvent.getNextAttemptAt());
    Assertions.assertNull(outboxEvent.getPublishedAt());
    OrderCreatedEvent event = jsonMapper.readValue(
            outboxEvent.getPayload(),
            OrderCreatedEvent.class);
    Assertions.assertEquals(order.getId(), event.orderId());
    Assertions.assertEquals(2, event.items().size());
    Assertions.assertTrue(event.items().contains(
            new OrderItemEvent(10L, 2)));
    Assertions.assertTrue(event.items().contains(
            new OrderItemEvent(15L, 3)));
  }

  @Test
  void shouldRollbackOrderCreationWhenOutboxFails() {
    Customer customer = saveCustomer("matt@example.com");
    OrderCreateRequest request = new OrderCreateRequest(
            customer.getId(), List.of(new OrderItemCreateRequest(10L, 2)));
    OutboxService spy = AopTestUtils.getUltimateTargetObject(outboxService);
    doThrow(new IllegalStateException("Outbox failure"))
            .when(spy)
            .enqueue(anyLong(), eq("orders.created"), any());
    Assertions.assertThrows(IllegalStateException.class,
            () -> orderService.createOrder(request));
    Assertions.assertEquals(0, orderRepository.count());
    Assertions.assertEquals(0, orderItemRepository.count());
    Assertions.assertEquals(0, outboxEventRepository.count());
    Assertions.assertEquals(1, customerRepository.count());
  }

  @Test
  void shouldChangeOrderStatusToAccepted() {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    orderService.changeOrderStatus(order.getId(), ProcessingStatus.ACCEPTED, null);
    Order updated = orderRepository.findById(order.getId())
            .orElseThrow();
    Assertions.assertEquals(OrderStatus.ACCEPTED, updated.getOrderStatus());
    List<OutboxEvent> events = outboxEventRepository.findAll();
    Assertions.assertEquals(1, events.size());
    OutboxEvent event = events.getFirst();
    Assertions.assertEquals(order.getId(), event.getAggregateId());
    Assertions.assertEquals("orders.finalized", event.getTopic());
    Assertions.assertEquals("OrderFinalizedEvent", event.getEventType());
    Assertions.assertEquals(OutboxStatus.PENDING, event.getStatus());
  }

  @Test
  void shouldChangeOrderStatusToRejected() {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    String reason = "Insufficient stock";
    orderService.changeOrderStatus(order.getId(), ProcessingStatus.REJECTED, reason);
    Order updated = orderRepository.findById(order.getId())
            .orElseThrow();
    Assertions.assertEquals(OrderStatus.REJECTED, updated.getOrderStatus());
    List<OutboxEvent> events = outboxEventRepository.findAll();
    Assertions.assertEquals(1, events.size());
    OutboxEvent outboxEvent = events.getFirst();
    OrderFinalizedEvent event = jsonMapper.readValue(outboxEvent.getPayload(), OrderFinalizedEvent.class);
    Assertions.assertEquals(order.getId(), event.orderId());
    Assertions.assertEquals(ProcessingStatus.REJECTED, event.status());
    Assertions.assertEquals(reason, event.reason());
    Assertions.assertNotNull(event.finalizedAt());
  }

  @Test
  void shouldIgnoreDuplicateProcessedEvent() {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    orderService.changeOrderStatus(order.getId(), ProcessingStatus.ACCEPTED, null);
    orderService.changeOrderStatus(order.getId(), ProcessingStatus.ACCEPTED, null);
    Order updated = orderRepository.findById(order.getId())
            .orElseThrow();
    Assertions.assertEquals(OrderStatus.ACCEPTED, updated.getOrderStatus());
    Assertions.assertEquals(1, outboxEventRepository.count());
  }

  @Test
  void shouldRejectConflictingProcessedEvent() {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    orderService.changeOrderStatus(order.getId(), ProcessingStatus.ACCEPTED, null);
    Assertions.assertThrows(OrderAlreadyProcessedException.class,
            () -> orderService.changeOrderStatus(
                    order.getId(), ProcessingStatus.REJECTED, "Rejected after acceptance"));
    Order updated = orderRepository.findById(order.getId())
            .orElseThrow();
    Assertions.assertEquals(OrderStatus.ACCEPTED, updated.getOrderStatus());
    Assertions.assertEquals(1, outboxEventRepository.count());
  }

  @Test
  void shouldReturnPersistedOrderWithItems() throws Exception {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    mockMvc.perform(get("/api/orders/{id}", order.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(order.getId()))
            .andExpect(jsonPath("$.customerId").value(customer.getId()))
            .andExpect(jsonPath("$.orderStatus").value("PENDING"))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].productId").value(10))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].orderId").value(order.getId()));
  }

  @Test
  void shouldReturnOrdersFilteredByCustomerId() throws Exception {
    Customer matt = saveCustomer("matt@example.com");
    Customer foggy = saveCustomer("foggy@example.com");
    Order mattOrder = savePendingOrder(matt);
    savePendingOrder(foggy);
    mockMvc.perform(get("/api/orders")
                    .param("customerId", matt.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(mattOrder.getId()))
            .andExpect(jsonPath("$[0].customerId").value(matt.getId()));
  }

  @Test
  void shouldPublishOrderCreatedEvent() {
    Customer customer = saveCustomer("matt@example.com");
    OrderCreateRequest request = new OrderCreateRequest(customer.getId(), List.of(
            new OrderItemCreateRequest(10L, 2),
            new OrderItemCreateRequest(15L, 3)));
    try (KafkaConsumer<String, String> consumer = createKafkaConsumer("orders.created")) {
      OrderDTO order = orderService.createOrder(request);
      Assertions.assertEquals(1, outboxEventRepository.count());
      publishOutboxEvents();
      ConsumerRecord<String, String> record = awaitKafkaRecord(consumer, "orders.created", order.id());
      OrderCreatedEvent event = jsonMapper.readValue(record.value(), OrderCreatedEvent.class);
      Assertions.assertEquals(order.id(), event.orderId());
      Assertions.assertEquals(2, event.items().size());
      Assertions.assertTrue(event.items().contains(new OrderItemEvent(10L, 2)));
      Assertions.assertTrue(event.items().contains(new OrderItemEvent(15L, 3)));
      OutboxEvent outbox = outboxEventRepository.findAll().getFirst();
      Assertions.assertEquals(OutboxStatus.SENT, outbox.getStatus());
      Assertions.assertNotNull(outbox.getPublishedAt());
      Assertions.assertEquals(0, outbox.getAttempts());
    }
  }

  @Test
  void shouldConsumeOrderProcessedEvent() throws Exception {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    Long orderId = order.getId();
    OrderProcessedEvent event = new OrderProcessedEvent(orderId, ProcessingStatus.ACCEPTED, null);
    kafkaTemplate.send("orders.processed", orderId.toString(), jsonMapper.writeValueAsString(event))
            .get(10, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> {
              Order updated = orderRepository.findById(orderId)
                      .orElseThrow();
              Assertions.assertEquals(OrderStatus.ACCEPTED, updated.getOrderStatus());
              List<OutboxEvent> events = outboxEventRepository.findAll();
              Assertions.assertEquals(1, events.size());
              Assertions.assertEquals("orders.finalized", events.getFirst().getTopic());
            });
  }

  @Test
  void shouldPublishOrderFinalizedEvent() throws Exception {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    Long orderId = order.getId();
    try (KafkaConsumer<String, String> consumer = createKafkaConsumer("orders.finalized")) {
      orderService.changeOrderStatus(orderId, ProcessingStatus.REJECTED, "Insufficient stock");
      Assertions.assertEquals(1, outboxEventRepository.count());
      publishOutboxEvents();
      ConsumerRecord<String, String> record = awaitKafkaRecord(consumer, "orders.finalized", orderId);
      OrderFinalizedEvent event = jsonMapper.readValue(record.value(), OrderFinalizedEvent.class);
      Assertions.assertEquals(orderId, event.orderId());
      Assertions.assertEquals(ProcessingStatus.REJECTED, event.status());
      Assertions.assertEquals("Insufficient stock", event.reason());
      Assertions.assertNotNull(event.finalizedAt());
      OutboxEvent outbox = outboxEventRepository.findAll().getFirst();
      Assertions.assertEquals("orders.finalized", outbox.getTopic());
      Assertions.assertEquals(OutboxStatus.SENT, outbox.getStatus());
      Assertions.assertNotNull(outbox.getPublishedAt());
    }
  }

  @Test
  void shouldRollbackStatusChangeWhenOutboxFails() {
    Customer customer = saveCustomer("matt@example.com");
    Order order = savePendingOrder(customer);
    OutboxService spy = AopTestUtils.getUltimateTargetObject(outboxService);
    doThrow(new IllegalStateException("Outbox failure"))
            .when(spy)
            .enqueue(anyLong(), eq("orders.finalized"), any());
    Assertions.assertThrows(IllegalStateException.class,
            () -> orderService.changeOrderStatus(order.getId(), ProcessingStatus.ACCEPTED, null));
    Order updated = orderRepository.findById(order.getId())
            .orElseThrow();
    Assertions.assertEquals(OrderStatus.PENDING, updated.getOrderStatus());
    Assertions.assertEquals(0, outboxEventRepository.count());
  }
}
