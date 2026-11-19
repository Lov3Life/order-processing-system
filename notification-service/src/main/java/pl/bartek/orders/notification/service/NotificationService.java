package pl.bartek.orders.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.notification.dto.NotificationDTO;
import pl.bartek.orders.notification.entity.Notification;
import pl.bartek.orders.notification.exception.NotificationInvalidData;
import pl.bartek.orders.notification.exception.NotificationNotFoundException;
import pl.bartek.orders.notification.repository.NotificationRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional(readOnly = true)
  public List<NotificationDTO> getAllNotifications(Long orderId) {
    Stream<Notification> response;
    if (orderId == null)
      response = notificationRepository.findAll().stream();
    else
      response = notificationRepository.findByOrderId(orderId).stream();
    return response
            .map(NotificationDTO::FromNotification)
            .toList();
  }

  public NotificationDTO getNotification(Long notificationId) {
    return NotificationDTO.FromNotification(notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId)));
  }

  @Transactional
  public void createNotification(Long orderId, ProcessingStatus status, String reason) {
    if(orderId == null || orderId <= 0)
      throw new NotificationInvalidData("Order is must be positive.");
    if (status == null)
      throw new NotificationInvalidData("Status cannot be null.");
    String message = generateMessage(orderId, status, reason);
    if (message.length() > 500)
      throw new NotificationInvalidData("Message cannot exceed 500 characters.");
    Optional<Notification> existing = notificationRepository.findByOrderId(orderId);
    if(existing.isPresent()) {
      Notification notification = existing.get();
      if (notification.getStatus() == status && notification.getMessage().equals(message))
        return;
      throw new NotificationInvalidData("Conflicting notification for order: " + orderId);
    }
    Notification notification = new Notification(orderId, status, message);
    notificationRepository.save(notification);
  }

  private String generateMessage(Long orderId, ProcessingStatus status, String reason) {
    return switch (status) {
      case ACCEPTED -> "Order " + orderId + " has been accepted.";
      case REJECTED -> {
        String message = "Order " + orderId + " has been rejected.";
        if (reason != null && !reason.isBlank())
          message += " Reason: " + reason.strip();
        yield message;
      }
    };
  }
}
