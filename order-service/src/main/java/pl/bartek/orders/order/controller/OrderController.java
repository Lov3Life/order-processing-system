package pl.bartek.orders.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.bartek.orders.order.dto.OrderCreateRequest;
import pl.bartek.orders.order.dto.OrderDTO;
import pl.bartek.orders.order.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping
  public List<OrderDTO> getOrders(@RequestParam(required = false) Long customerId) {
    return orderService.getOrders(customerId);
  }

  @GetMapping("/{id}")
  public OrderDTO getOrder(@PathVariable Long id){
    return orderService.getOrder(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderDTO createOrder(@RequestBody @Valid OrderCreateRequest request) {
    return orderService.createOrder(request);
  }
}
