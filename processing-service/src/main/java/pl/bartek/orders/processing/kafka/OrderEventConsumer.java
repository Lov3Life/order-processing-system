package pl.bartek.orders.processing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.processing.service.OrderProcessingService;

@Component
public class OrderEventConsumer {

  private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

  private final OrderProcessingService orderProcessingService;

  public OrderEventConsumer(OrderProcessingService orderProcessingService) {
    this.orderProcessingService = orderProcessingService;
  }

  @KafkaListener(topics = "orders.created")
  public void receiveOrder(OrderCreatedEvent event) {
    logger.info("Received order {}", event.orderId());
    orderProcessingService.processOrder(event);
  }
}
