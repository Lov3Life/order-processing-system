package pl.bartek.orders.processing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderProcessedEvent;
import pl.bartek.orders.processing.dto.OrderProcessingResult;
import pl.bartek.orders.processing.service.OrderProcessingService;

@Component
public class OrderEventConsumer {

  private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

  private final OrderProcessingService orderProcessingService;
  private final OrderEventProducer orderEventProducer;

  public OrderEventConsumer(OrderProcessingService orderProcessingService, OrderEventProducer orderEventProducer) {
    this.orderProcessingService = orderProcessingService;
    this.orderEventProducer = orderEventProducer;
  }

  @KafkaListener(topics = "orders.created")
  public void receiveOrder(OrderCreatedEvent event){
    logger.info("Received Order Event {}", event);
    OrderProcessingResult result = orderProcessingService.processOrder(event);
    OrderProcessedEvent processedEvent = new OrderProcessedEvent(
            event.orderId(),
            result.status(),
            result.reason()
    );
    orderEventProducer.sendOrderProcessed(processedEvent);
  }
}
