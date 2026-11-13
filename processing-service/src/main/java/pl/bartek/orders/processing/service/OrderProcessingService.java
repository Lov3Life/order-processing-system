package pl.bartek.orders.processing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderItemEvent;
import pl.bartek.orders.event.OrderProcessedEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.outbox.service.OutboxService;
import pl.bartek.orders.processing.dto.OrderProcessingResult;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.repository.ProcessedOrderRepository;
import pl.bartek.orders.processing.repository.ProductRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderProcessingService {

  private final ProductRepository productRepository;
  private final ProcessedOrderRepository processedOrderRepository;
  private final OutboxService outboxService;
  private static final Logger logger = LoggerFactory.getLogger(OrderProcessingService.class);

  public OrderProcessingService(ProductRepository productRepository,
                                ProcessedOrderRepository processedOrderRepository,
                                OutboxService outboxService) {
    this.productRepository = productRepository;
    this.processedOrderRepository = processedOrderRepository;
    this.outboxService = outboxService;
  }

  @Transactional
  public void processOrder(OrderCreatedEvent event) {
    int claimed = processedOrderRepository.tryClaim(event.orderId());
    if (claimed == 0) {
      logger.info("Order {} already processed", event.orderId());
      return;
    }
    OrderProcessingResult result = processInventory(event);
    int updated = processedOrderRepository.markProcessed(
            event.orderId(), result.status().name(), result.reason());
    if (updated != 1)
      throw new IllegalStateException("Could not mark order as processed: " + event.orderId());
    OrderProcessedEvent processedEvent = new OrderProcessedEvent(
            event.orderId(), result.status(), result.reason());
    outboxService.enqueue(event.orderId(), "orders.processed", processedEvent);
  }

  public OrderProcessingResult processInventory(OrderCreatedEvent order) {
    List<OrderItemEvent> items = new ArrayList<>(order.items());
    items.sort(Comparator.comparingLong(OrderItemEvent::productId));
    List<Product> products = new ArrayList<>();
    for(OrderItemEvent item : items) {
      Product product = productRepository.findByIdForUpdate(item.productId())
              .orElse(null);
      if(product == null)
        return new OrderProcessingResult(ProcessingStatus.REJECTED,
                "product with id " + item.productId() + " not found");
      products.add(product);
      if(product.getStockQuantity() < item.quantity())
        return new OrderProcessingResult(ProcessingStatus.REJECTED,
                "Insufficient quantity(" + item.quantity() + ") of the product with ID: " + item.productId());
    }
    for(int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      product.subStockQuantity(items.get(i).quantity());
      productRepository.save(product);
    }
    return new OrderProcessingResult(ProcessingStatus.ACCEPTED, null);
  }
}
