package pl.bartek.orders.processing.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderItemEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.outbox.service.OutboxService;
import pl.bartek.orders.processing.dto.OrderProcessingResult;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.repository.ProcessedOrderRepository;
import pl.bartek.orders.processing.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderProcessingServiceTest {

  @Mock
  private ProductRepository productRepository;

  @Mock
  private ProcessedOrderRepository processedOrderRepository;

  @Mock
  private OutboxService outboxService;

  private OrderProcessingService orderProcessingService;

  @BeforeEach
  void setUp() {
    orderProcessingService = new OrderProcessingService(productRepository, processedOrderRepository, outboxService);
  }

  @Test
  void shouldAcceptOrderWhenProductsAvailable() {
    Product product1 = new Product("24-inch monitor", 10);
    Product product2 = new Product("27-inch monitor", 5);
    OrderCreatedEvent event = new OrderCreatedEvent(1L, List.of(
            new OrderItemEvent(2L, 3),
            new OrderItemEvent(1L, 2)));
    when(productRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.of(product1));
    when(productRepository.findByIdForUpdate(2L))
            .thenReturn(Optional.of(product2));
    OrderProcessingResult result = orderProcessingService.processInventory(event);
    Assertions.assertNotNull(result);
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, result.status());
    Assertions.assertNull(result.reason());
    Assertions.assertEquals(8, product1.getStockQuantity());
    Assertions.assertEquals(2, product2.getStockQuantity());
    verify(productRepository).save(product1);
    verify(productRepository).save(product2);
  }

  @Test
  void shouldAcceptOrderWhenProductsAvailableAndLeveZeroStockQuantity() {
    Product product1 = new Product("24-inch monitor", 10);
    Product product2 = new Product("27-inch monitor", 5);
    OrderCreatedEvent event = new OrderCreatedEvent(1L, List.of(
            new OrderItemEvent(2L, 5),
            new OrderItemEvent(1L, 10)));
    when(productRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.of(product1));
    when(productRepository.findByIdForUpdate(2L))
            .thenReturn(Optional.of(product2));
    OrderProcessingResult result = orderProcessingService.processInventory(event);
    Assertions.assertNotNull(result);
    Assertions.assertEquals(ProcessingStatus.ACCEPTED, result.status());
    Assertions.assertNull(result.reason());
    Assertions.assertEquals(0, product1.getStockQuantity());
    Assertions.assertEquals(0, product2.getStockQuantity());
    verify(productRepository).save(product1);
    verify(productRepository).save(product2);
  }

  @Test
  void shouldRejectOrderWhenProductNotFound() {
    OrderCreatedEvent event = new OrderCreatedEvent(1L, List.of(
            new OrderItemEvent(2L, 3),
            new OrderItemEvent(1L, 2)));
    when(productRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.empty());
    OrderProcessingResult result = orderProcessingService.processInventory(event);
    Assertions.assertNotNull(result);
    Assertions.assertEquals(ProcessingStatus.REJECTED, result.status());
    Assertions.assertNotNull(result.reason());
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldRejectOrderWhenInsufficientStockQuantity(){
    Product product = new Product("24-inch monitor", 10);
    OrderCreatedEvent event = new OrderCreatedEvent(1L, List.of(new OrderItemEvent(1L, 11)));
    when(productRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.of(product));
    OrderProcessingResult result = orderProcessingService.processInventory(event);
    Assertions.assertNotNull(result);
    Assertions.assertEquals(ProcessingStatus.REJECTED, result.status());
    Assertions.assertNotNull(result.reason());
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldSkipAlreadyProcessedOrder() {
    OrderCreatedEvent event = new OrderCreatedEvent(1L, List.of(new OrderItemEvent(2L, 3)));
    when(processedOrderRepository.tryClaim(1L))
            .thenReturn(0);
    orderProcessingService.processOrder(event);
    verify(processedOrderRepository).tryClaim(1L);
    verifyNoInteractions(productRepository, outboxService);
    verify(processedOrderRepository, never())
            .markProcessed(anyLong(), anyString(), any());
  }
}
