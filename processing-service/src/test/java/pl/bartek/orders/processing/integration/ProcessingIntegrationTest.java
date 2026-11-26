package pl.bartek.orders.processing.integration;

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
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderItemEvent;
import pl.bartek.orders.event.OrderProcessedEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.enums.OutboxStatus;
import pl.bartek.orders.outbox.publisher.OutboxPublisher;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.outbox.service.OutboxService;
import pl.bartek.orders.outbox.service.OutboxStatusService;
import pl.bartek.orders.processing.entity.ProcessedOrder;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.repository.ProcessedOrderRepository;
import pl.bartek.orders.processing.repository.ProductRepository;
import pl.bartek.orders.processing.service.OrderProcessingService;
import pl.bartek.orders.processing.service.ProductService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class ProcessingIntegrationTest {

  @Container
  @ServiceConnection
  static final MySQLContainer mysql = new MySQLContainer(DockerImageName
          .parse("mysql:8.4.13"))
          .withDatabaseName("order-processing-system-inventory_db")
          .withUsername("test")
          .withPassword("test")
          .withInitScript("database/02-inventory_db.sql");

  @Container
  @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer(DockerImageName
          .parse("apache/kafka:4.3.2"));

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository productRepository;

  @Autowired
  private ProcessedOrderRepository processedOrderRepository;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private ProductService productService;

  @Autowired
  private OrderProcessingService orderProcessingService;

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
    outboxEventRepository.deleteAll();
    processedOrderRepository.deleteAll();
    productRepository.deleteAll();
  }

  private static final AtomicLong ORDER_IDS = new AtomicLong(10_000);

  private Callable<Void> concurrentTask(Runnable action, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS))
        throw new AssertionError("Start timeout");
      action.run();
      return null;
    };
  }

  private void runConcurrently(Runnable first, Runnable second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Void> firstResult = executor.submit(concurrentTask(first, ready, start));
      Future<Void> secondResult = executor.submit(concurrentTask(second, ready, start));
      Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      firstResult.get(30, TimeUnit.SECONDS);
      secondResult.get(30, TimeUnit.SECONDS);
    } finally {
      start.countDown();
    }
  }

  private KafkaConsumer<String, String> createKafkaConsumer(String topic) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
    consumer.subscribe(List.of(topic));
    return consumer;
  }

  private ConsumerRecord<String, String> awaitKafkaRecord(KafkaConsumer<String, String> consumer,
                                                          String topic, Long orderId) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        if (topic.equals(record.topic()) && orderId.toString().equals(record.key())) {
          return record;
        }
      }
    }
    throw new AssertionError("Kafka record not received for order: " + orderId);
  }

  private void publishOutboxEvents() {
    OutboxPublisher publisher = new OutboxPublisher(outboxEventRepository, outboxStatusService, kafkaTemplate);
    publisher.publishPendingEvents();
  }

  private Product saveProduct(String name, int stockQuantity) {
    return productRepository.save(new Product(name, stockQuantity));
  }

  private OrderCreatedEvent createOrderEvent(Long orderId, Long productId, int quantity) {
    return new OrderCreatedEvent(orderId, List.of(new OrderItemEvent(productId, quantity)));
  }

  private OrderCreatedEvent createOrderEventMultiItems(Long orderId, Long productId1, int quantity1, Long productId2, int quantity2) {
    return new OrderCreatedEvent(orderId, List.of(
            new OrderItemEvent(productId1, quantity1),
            new OrderItemEvent(productId2, quantity2)));
  }

  private void assertPendingOutboxEvent(ProcessingStatus expectedStatus) {
    List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
    Assertions.assertEquals(1, outboxEvents.size());
    OutboxEvent outboxEvent = outboxEvents.getFirst();
    Assertions.assertNotNull(outboxEvent.getId());
    Assertions.assertEquals(1L, outboxEvent.getAggregateId());
    Assertions.assertEquals("OrderProcessedEvent", outboxEvent.getEventType());
    Assertions.assertEquals("orders.processed", outboxEvent.getTopic());
    Assertions.assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
    Assertions.assertEquals(0, outboxEvent.getAttempts());
    Assertions.assertNotNull(outboxEvent.getCreatedAt());
    Assertions.assertNotNull(outboxEvent.getNextAttemptAt());
    Assertions.assertNull(outboxEvent.getPublishedAt());
    OrderProcessedEvent event = jsonMapper.readValue(outboxEvent.getPayload(), OrderProcessedEvent.class);
    Assertions.assertEquals(1L, event.orderId());
    if(ProcessingStatus.ACCEPTED == expectedStatus){
      Assertions.assertEquals(ProcessingStatus.ACCEPTED, event.status());
      Assertions.assertNull(event.reason());
    } else if(ProcessingStatus.REJECTED == expectedStatus) {
      Assertions.assertEquals(ProcessingStatus.REJECTED, event.status());
      Assertions.assertNotNull(event.reason());
    }
  }

  @Test
  void shouldCreateProduct() throws Exception {
    String request = """
            {
              "name": "24-inches monitor",
              "stockQuantity": 3
            }
            """;
    mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
    List<Product> products = productRepository.findAll();
    Assertions.assertEquals(1, products.size());
    Product product = products.getFirst();
    Assertions.assertNotNull(product.getId());
    Assertions.assertEquals("24-inches monitor", product.getName());
    Assertions.assertEquals(3, product.getStockQuantity());
  }

  @Test
  void shouldReturnPersistedProduct() throws Exception {
    Long productId = saveProduct("24-inches monitor", 3).getId();
    mockMvc.perform(get("/api/products/{id}",productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(productId))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
  }

  @Test
  void shouldAddStockQuantity() throws Exception {
    Long productId = saveProduct("24-inches monitor", 3).getId();
    mockMvc.perform(patch("/api/products/addStockQuantity/{id}", productId)
                    .param("quantity", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(productId))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(7));
    Product updated = productRepository.findById(productId)
            .orElseThrow();
    Assertions.assertEquals(7, updated.getStockQuantity());
  }

  @Test
  void shouldSubtractStockQuantity() throws Exception {
    Long productId = saveProduct("24-inches monitor", 7).getId();
    mockMvc.perform(patch("/api/products/subtractStockQuantity/{id}", productId)
                    .param("quantity", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(productId))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
    Product updated = productRepository.findById(productId)
            .orElseThrow();
    Assertions.assertEquals(3, updated.getStockQuantity());
  }

  @Test
  void shouldProcessOrderAndDecreaseStock() {
    Long productId = saveProduct("24-inches monitor", 10).getId();
    orderProcessingService.processOrder(createOrderEvent(1L, productId, 8));
    Product product = productRepository.findById(productId)
            .orElseThrow();
    Assertions.assertEquals(2, product.getStockQuantity());
    List<ProcessedOrder> processedOrders = processedOrderRepository.findAll();
    Assertions.assertEquals(1, processedOrders.size());
    ProcessedOrder processedOrder = processedOrders.getFirst();
    Assertions.assertNotNull(processedOrder.getId());
    Assertions.assertEquals("ACCEPTED", processedOrder.getStatus());
    Assertions.assertEquals(1L, processedOrder.getId());
    Assertions.assertNull(processedOrder.getReason());
    Assertions.assertNotNull(processedOrder.getProcessedAt());
    assertPendingOutboxEvent(ProcessingStatus.ACCEPTED);
  }

  @Test
  void shouldRejectOrderWhenStockInsufficient() {
    Long productId = saveProduct("24-inches monitor", 2).getId();
    orderProcessingService.processOrder(createOrderEvent(1L, productId, 5));
    Product product = productRepository.findById(productId)
            .orElseThrow();
    Assertions.assertEquals(2, product.getStockQuantity());
    List<ProcessedOrder> processedOrders = processedOrderRepository.findAll();
    Assertions.assertEquals(1, processedOrders.size());
    ProcessedOrder processedOrder = processedOrders.getFirst();
    Assertions.assertNotNull(processedOrder.getId());
    Assertions.assertEquals("REJECTED", processedOrder.getStatus());
    Assertions.assertEquals(1L, processedOrder.getId());
    Assertions.assertNotNull(processedOrder.getReason());
    Assertions.assertNotNull(processedOrder.getProcessedAt());
    assertPendingOutboxEvent(ProcessingStatus.REJECTED);
  }

  @Test
  void shouldRejectOrderWhenProductNotFound() {
    orderProcessingService.processOrder(createOrderEvent(1L, 1L, 5));
    Assertions.assertEquals(0, productRepository.findAll().size());
    List<ProcessedOrder> processedOrders = processedOrderRepository.findAll();
    Assertions.assertEquals(1, processedOrders.size());
    ProcessedOrder processedOrder = processedOrders.getFirst();
    Assertions.assertNotNull(processedOrder.getId());
    Assertions.assertEquals("REJECTED", processedOrder.getStatus());
    Assertions.assertEquals(1L, processedOrder.getId());
    Assertions.assertNotNull(processedOrder.getReason());
    Assertions.assertNotNull(processedOrder.getProcessedAt());
    assertPendingOutboxEvent(ProcessingStatus.REJECTED);
  }

  @Test
  void shouldNotPartiallyDecreaseStock() {
    Long productId1 = saveProduct("24-inches monitor", 8).getId();
    Long productId2 = saveProduct("27-inches monitor", 2).getId();
    orderProcessingService.processOrder(createOrderEventMultiItems(1L, productId1, 5, productId2, 5));
    Product product1 = productRepository.findById(productId1)
            .orElseThrow();
    Product product2 = productRepository.findById(productId2)
            .orElseThrow();
    Assertions.assertEquals(8, product1.getStockQuantity());
    Assertions.assertEquals(2, product2.getStockQuantity());
    List<ProcessedOrder> processedOrders = processedOrderRepository.findAll();
    Assertions.assertEquals(1, processedOrders.size());
    ProcessedOrder processedOrder = processedOrders.getFirst();
    Assertions.assertNotNull(processedOrder.getId());
    Assertions.assertEquals("REJECTED", processedOrder.getStatus());
    Assertions.assertEquals(1L, processedOrder.getId());
    Assertions.assertNotNull(processedOrder.getReason());
    Assertions.assertNotNull(processedOrder.getProcessedAt());
    assertPendingOutboxEvent(ProcessingStatus.REJECTED);
  }

  @Test
  void shouldIgnoreAlreadyProcessedOrder() {
    Long productId = saveProduct("24-inches monitor", 12).getId();
    OrderCreatedEvent validEvent = createOrderEvent(1L, productId, 5);
    orderProcessingService.processOrder(validEvent);
    orderProcessingService.processOrder(validEvent);
    Product product = productRepository.findById(productId)
            .orElseThrow();
    Assertions.assertEquals(7, product.getStockQuantity());
    List<ProcessedOrder> processedOrders = processedOrderRepository.findAll();
    Assertions.assertEquals(1, processedOrders.size());
    ProcessedOrder processedOrder = processedOrders.getFirst();
    Assertions.assertNotNull(processedOrder.getId());
    Assertions.assertEquals("ACCEPTED", processedOrder.getStatus());
    Assertions.assertEquals(1L, processedOrder.getId());
    Assertions.assertNull(processedOrder.getReason());
    Assertions.assertNotNull(processedOrder.getProcessedAt());
    assertPendingOutboxEvent(ProcessingStatus.ACCEPTED);
  }

  @Test
  void shouldRollbackProcessingWhenOutboxFails() {
    Product product = saveProduct("24-inches monitor", 10);
    OrderCreatedEvent event = createOrderEvent(1L, product.getId(), 8);
    OutboxService spy = AopTestUtils.getUltimateTargetObject(outboxService);
    doThrow(new IllegalStateException("Outbox failure"))
            .when(spy)
            .enqueue(eq(1L), eq("orders.processed"), any(OrderProcessedEvent.class));
    Assertions.assertThrows(IllegalStateException.class,
            () -> orderProcessingService.processOrder(event));
    Product updated = productRepository.findById(product.getId())
            .orElseThrow();
    Assertions.assertEquals(10, updated.getStockQuantity());
    Assertions.assertFalse(processedOrderRepository.existsById(1L));
    Assertions.assertEquals(0, outboxEventRepository.count());
  }

  @Test
  void shouldHandleConcurrentOrdersForSameProduct() throws Exception {
    Product product = saveProduct("24-inches monitor", 1);
    Long firstOrderId = ORDER_IDS.incrementAndGet();
    Long secondOrderId = ORDER_IDS.incrementAndGet();
    OrderCreatedEvent firstEvent = createOrderEvent(firstOrderId, product.getId(), 1);
    OrderCreatedEvent secondEvent = createOrderEvent(secondOrderId, product.getId(), 1);
    runConcurrently(
            () -> orderProcessingService.processOrder(firstEvent),
            () -> orderProcessingService.processOrder(secondEvent));
    Product updated = productRepository.findById(product.getId())
            .orElseThrow();
    Assertions.assertEquals(0, updated.getStockQuantity());
    List<ProcessedOrder> processedOrders = processedOrderRepository.findAll();
    Assertions.assertEquals(2, processedOrders.size());
    long accepted = processedOrders.stream()
            .filter(order -> order.getStatus().equals("ACCEPTED"))
            .count();
    long rejected = processedOrders.stream()
            .filter(order -> order.getStatus().equals("REJECTED"))
            .count();
    Assertions.assertEquals(1, accepted);
    Assertions.assertEquals(1, rejected);
    Set<Long> processedIds = processedOrders.stream()
            .map(ProcessedOrder::getId)
            .collect(Collectors.toSet());
    Assertions.assertEquals(Set.of(firstOrderId, secondOrderId), processedIds);
    List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
    Assertions.assertEquals(2, outboxEvents.size());
    Set<ProcessingStatus> statuses = new java.util.HashSet<>();
    for (OutboxEvent outboxEvent : outboxEvents) {
      Assertions.assertEquals("orders.processed", outboxEvent.getTopic());
      OrderProcessedEvent event = jsonMapper.readValue(outboxEvent.getPayload(), OrderProcessedEvent.class);
      statuses.add(event.status());
    }
    Assertions.assertEquals(Set.of(ProcessingStatus.ACCEPTED, ProcessingStatus.REJECTED), statuses);
  }

  @Test
  void shouldHandleConcurrentDuplicateOrder() throws Exception {
    Product product = saveProduct("24-inches monitor", 10);
    Long orderId = ORDER_IDS.incrementAndGet();
    OrderCreatedEvent event = createOrderEvent(orderId, product.getId(), 3);
    runConcurrently(
            () -> orderProcessingService.processOrder(event),
            () -> orderProcessingService.processOrder(event));
    Product updated = productRepository.findById(product.getId())
            .orElseThrow();
    Assertions.assertEquals(7, updated.getStockQuantity());
    Assertions.assertEquals(1, processedOrderRepository.count());
    Assertions.assertEquals(1, outboxEventRepository.count());
    ProcessedOrder processedOrder = processedOrderRepository.findById(orderId)
            .orElseThrow();
    Assertions.assertEquals("ACCEPTED", processedOrder.getStatus());
    OutboxEvent outboxEvent = outboxEventRepository.findAll().getFirst();
    Assertions.assertEquals(orderId, outboxEvent.getAggregateId());
    OrderProcessedEvent result = jsonMapper.readValue(outboxEvent.getPayload(), OrderProcessedEvent.class);
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, result.status());
  }

  @Test
  void shouldConsumeOrderCreatedEvent() throws Exception {
    Product product = saveProduct("24-inches monitor", 10);
    Long orderId = ORDER_IDS.incrementAndGet();
    OrderCreatedEvent event = createOrderEvent(orderId, product.getId(), 3);
    kafkaTemplate.send("orders.created", orderId.toString(), jsonMapper.writeValueAsString(event))
            .get(10, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> {
              Product updated = productRepository.findById(product.getId())
                      .orElseThrow();
              Assertions.assertEquals(7, updated.getStockQuantity());
              ProcessedOrder processedOrder = processedOrderRepository.findById(orderId)
                      .orElseThrow();
              Assertions.assertEquals("ACCEPTED", processedOrder.getStatus());
              List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
              Assertions.assertEquals(1, outboxEvents.size());
              Assertions.assertEquals(orderId, outboxEvents.getFirst().getAggregateId());
              Assertions.assertEquals("orders.processed", outboxEvents.getFirst().getTopic());
              Assertions.assertEquals(OutboxStatus.PENDING, outboxEvents.getFirst().getStatus());
            });
  }

  @Test
  void shouldPublishOrderProcessedEvent() throws Exception {
    Product product = saveProduct("24-inches monitor", 10);
    Long orderId = ORDER_IDS.incrementAndGet();
    OrderCreatedEvent event = createOrderEvent(orderId, product.getId(), 3);
    try (KafkaConsumer<String, String> consumer = createKafkaConsumer("orders.processed")) {
      orderProcessingService.processOrder(event);
      List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
      Assertions.assertEquals(1, outboxEvents.size());
      OutboxEvent outboxEvent = outboxEvents.getFirst();
      Assertions.assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
      publishOutboxEvents();
      ConsumerRecord<String, String> record = awaitKafkaRecord(consumer, "orders.processed", orderId);
      OrderProcessedEvent result = jsonMapper.readValue(record.value(), OrderProcessedEvent.class);
      Assertions.assertEquals(orderId, result.orderId());
      Assertions.assertEquals(ProcessingStatus.ACCEPTED, result.status());
      Assertions.assertNull(result.reason());
      OutboxEvent updatedOutbox = outboxEventRepository.findById(outboxEvent.getId())
              .orElseThrow();
      Assertions.assertEquals(OutboxStatus.SENT, updatedOutbox.getStatus());
      Assertions.assertNotNull(updatedOutbox.getPublishedAt());
      Assertions.assertEquals(0, updatedOutbox.getAttempts());
    }
  }

  @Test
  void shouldIgnoreDuplicateKafkaEvent() throws Exception {
    Product product = saveProduct("24-inches monitor", 10);
    Long orderId = ORDER_IDS.incrementAndGet();
    Long markerOrderId = ORDER_IDS.incrementAndGet();
    OrderCreatedEvent event = createOrderEvent(orderId, product.getId(), 3);
    OrderCreatedEvent marker = createOrderEvent(markerOrderId, Long.MAX_VALUE, 1);
    String payload = jsonMapper.writeValueAsString(event);
    kafkaTemplate.send("orders.created", 0, orderId.toString(), payload)
            .get(10, TimeUnit.SECONDS);
    kafkaTemplate.send("orders.created", 0, orderId.toString(), payload)
            .get(10, TimeUnit.SECONDS);
    kafkaTemplate.send("orders.created", 0, markerOrderId.toString(),jsonMapper.writeValueAsString(marker))
            .get(10, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> {
              ProcessedOrder markerResult = processedOrderRepository.findById(markerOrderId)
                      .orElseThrow();
              Assertions.assertEquals("REJECTED", markerResult.getStatus());
            });
    Product updated = productRepository.findById(product.getId())
            .orElseThrow();
    Assertions.assertEquals(7, updated.getStockQuantity());
    ProcessedOrder processedOrder = processedOrderRepository.findById(orderId)
            .orElseThrow();
    Assertions.assertEquals("ACCEPTED", processedOrder.getStatus());
    long eventCount = outboxEventRepository.findAll().stream()
            .filter(outbox -> orderId.equals(outbox.getAggregateId()))
            .count();
    Assertions.assertEquals(1, eventCount);
    Assertions.assertEquals(2, processedOrderRepository.count());
  }
}
