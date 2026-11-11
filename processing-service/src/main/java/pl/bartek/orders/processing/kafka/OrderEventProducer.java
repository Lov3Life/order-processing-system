package pl.bartek.orders.processing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pl.bartek.orders.event.OrderProcessedEvent;

@Component
public class OrderEventProducer {

  private static final Logger logger = LoggerFactory.getLogger(OrderEventProducer.class);
  private final KafkaTemplate<String, OrderProcessedEvent> kafkaTemplate;

  public OrderEventProducer(KafkaTemplate<String, OrderProcessedEvent> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void sendOrderProcessed(OrderProcessedEvent event){
    kafkaTemplate.send(
            "orders.processed",
            event.orderId().toString(),
            event
    ).whenComplete((result, exception) -> {
      if (exception != null) {
        logger.error("Failed to send order processed {}", event.orderId(), exception);
      }else  {
        logger.info("Successfully sent processed order {} to Kafka", event.orderId());
      }
    });
  }
}
