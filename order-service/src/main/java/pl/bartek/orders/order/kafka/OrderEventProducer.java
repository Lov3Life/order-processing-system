package pl.bartek.orders.order.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.bartek.orders.event.OrderCreatedEvent;

@Component
public class OrderEventProducer {

  private static final Logger logger = LoggerFactory.getLogger(OrderEventProducer.class);
  private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

  public OrderEventProducer(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishOrder(OrderCreatedEvent event) {
    kafkaTemplate.send(
            "orders.created",
            event.orderId().toString(),
            event
    ).whenComplete((result, exception) -> {
      if (exception != null) {
        logger.error("Failed to publish order {}", event.orderId(), exception);
      }else {
        logger.info("Successfully published order {} to Kafka", event.orderId());
      }
    });
  }
}
