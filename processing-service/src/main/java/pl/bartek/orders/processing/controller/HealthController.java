package pl.bartek.orders.processing.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  public String health() {
    return "ProcessingService is up and running on port: 8082";
  }
}
