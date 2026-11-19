package pl.bartek.orders.notification.controller;

import org.springframework.web.bind.annotation.*;
import pl.bartek.orders.notification.dto.NotificationDTO;
import pl.bartek.orders.notification.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public List<NotificationDTO> getNotifications(@RequestParam(required = false) Long orderId) {
    return notificationService.getAllNotifications(orderId);
  }

  @GetMapping("/{id}")
  public NotificationDTO getNotification(@PathVariable Long id) {
    return notificationService.getNotification(id);
  }
}
