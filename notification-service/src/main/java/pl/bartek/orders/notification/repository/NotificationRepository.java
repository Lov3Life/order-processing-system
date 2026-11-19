package pl.bartek.orders.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.bartek.orders.notification.entity.Notification;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Optional<Notification> findByOrderId(Long orderId);
}
