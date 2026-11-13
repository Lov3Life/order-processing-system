package pl.bartek.orders.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderItemEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.order.dto.OrderCreateRequest;
import pl.bartek.orders.order.dto.OrderDTO;
import pl.bartek.orders.order.dto.OrderItemCreateRequest;
import pl.bartek.orders.order.dto.OrderSummaryDTO;
import pl.bartek.orders.order.entity.Customer;
import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.entity.OrderItem;
import pl.bartek.orders.order.enums.OrderStatus;
import pl.bartek.orders.order.exception.CustomerNotFoundException;
import pl.bartek.orders.order.exception.OrderAlreadyProcessedException;
import pl.bartek.orders.order.exception.OrderInvalidRequestDataException;
import pl.bartek.orders.order.exception.OrderNotFoundException;
import pl.bartek.orders.order.repository.CustomerRepository;
import pl.bartek.orders.order.repository.OrderRepository;
import pl.bartek.orders.outbox.service.OutboxService;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final OutboxService outboxService;

  public OrderService(OrderRepository orderRepository,
                      CustomerRepository customerRepository,
                      OutboxService outboxService) {
    this.orderRepository = orderRepository;
    this.customerRepository = customerRepository;
    this.outboxService = outboxService;
  }

  @Transactional(readOnly = true)
  public List<OrderSummaryDTO> getOrders(Long customerId) {
    List<Order> orderList;
    if(customerId == null)
      orderList = orderRepository.findAll();
    else
      orderList = orderRepository.findByCustomerId(customerId);
    return orderList.stream()
            .map(OrderSummaryDTO::fromOrder)
            .toList();
  }

  @Transactional(readOnly = true)
  public OrderDTO getOrder(Long id) {
    return orderRepository.findById(id)
            .map(OrderDTO::fromOrder)
            .orElseThrow(() -> new OrderNotFoundException(id));
  }

  @Transactional
  public OrderDTO createOrder(OrderCreateRequest request) {
    if(request.customerId() == null)
      throw new OrderInvalidRequestDataException("Customer id cannot be null");
    Customer customer = customerRepository.findById(request.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));
    Order order = new Order();
    order.setCustomer(customer);
    order.setOrderStatus(OrderStatus.PENDING);
    order.setOrderDate(Instant.now());
    if(request.items() == null || request.items().isEmpty())
      throw new OrderInvalidRequestDataException("Items cannot be null.");
    Set<Long> itemsIdCheck = request.items()
            .stream()
            .map(OrderItemCreateRequest::productId)
            .collect(Collectors.toSet());
    if(itemsIdCheck.size() != request.items().size())
      throw new OrderInvalidRequestDataException("Items should be unique");
    for(OrderItemCreateRequest item : request.items()) {
      if(item.productId() == null || item.productId() <= 0)
        throw new OrderInvalidRequestDataException("Product id cannot be less than 1.");
      if(item.quantity() == null || item.quantity() <= 0)
        throw new OrderInvalidRequestDataException("Item quantity cannot be less than 1.");
      OrderItem orderItem = new OrderItem();
      orderItem.setQuantity(item.quantity());
      orderItem.setProductId(item.productId());
      order.addOrderItem(orderItem);
    }
    OrderDTO savedOrder = OrderDTO.fromOrder(orderRepository.save(order));
    processOrder(savedOrder);
    return savedOrder;
  }

  @Transactional
  public void changeOrderStatus(Long orderId, ProcessingStatus status) {
    OrderStatus newStatus = OrderStatus.valueOf(status.name());
    int updated = orderRepository.updateStatusIfPending(orderId, newStatus, OrderStatus.PENDING);
    if (updated == 1)
      return;
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    if(order.getOrderStatus() == newStatus)
      return;
    throw new OrderAlreadyProcessedException(orderId);
  }

  private void processOrder(OrderDTO savedOrder) {
    List<OrderItemEvent> items = savedOrder.items().stream()
            .map(item ->
                    new OrderItemEvent(item.productId(), item.quantity()))
            .toList();
    OrderCreatedEvent event = new OrderCreatedEvent(savedOrder.id(), items);
    outboxService.enqueue(savedOrder.id(), "orders.created", event);
  }
}
