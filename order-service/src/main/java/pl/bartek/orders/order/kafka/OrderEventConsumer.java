package pl.bartek.orders.order.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pl.bartek.orders.event.OrderProcessedEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.order.service.OrderService;

@Component
public class OrderEventConsumer {

  private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

  private final OrderService orderService;

  public OrderEventConsumer(OrderService orderService) {
    this.orderService = orderService;
  }

  @KafkaListener(topics = "orders.processed")
  public void receiveOrderProcessed(OrderProcessedEvent event) {
    logger.info("Received processed order {}", event);
    if(event.status() == ProcessingStatus.REJECTED){
      orderService.changeOrderStatus(event.orderId(), event.status().toString());
    }
  }
}
