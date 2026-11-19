package pl.bartek.orders.notification.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pl.bartek.orders.event.OrderFinalizedEvent;
import pl.bartek.orders.notification.service.NotificationService;

@Component
public class NotificationEventConsumer {

  private static final Logger logger = LoggerFactory.getLogger(NotificationEventConsumer.class);

  private final NotificationService notificationService;

  public NotificationEventConsumer(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @KafkaListener(topics = "orders.finalized")
  public void receiveOrderFinalized(OrderFinalizedEvent event) {
    logger.info("Received order finalized event {}", event);
    notificationService.createNotification(event.orderId(), event.status(), event.reason());
    logger.info("Notification handled for order {}", event.orderId());
  }
}
