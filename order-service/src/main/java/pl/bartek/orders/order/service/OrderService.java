package pl.bartek.orders.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.order.dto.OrderCreateRequest;
import pl.bartek.orders.order.dto.OrderDTO;
import pl.bartek.orders.order.dto.OrderItemCreateRequest;
import pl.bartek.orders.order.entity.Customer;
import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.entity.OrderItem;
import pl.bartek.orders.order.enums.OrderStatus;
import pl.bartek.orders.order.exception.CustomerNotFoundException;
import pl.bartek.orders.order.exception.OrderInvalidRequestDataException;
import pl.bartek.orders.order.exception.OrderNotFoundException;
import pl.bartek.orders.order.repository.CustomerRepository;
import pl.bartek.orders.order.repository.OrderRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;

  public OrderService(OrderRepository orderRepository,
                      CustomerRepository customerRepository) {
    this.orderRepository = orderRepository;
    this.customerRepository = customerRepository;
  }

  public List<OrderDTO> getOrders(Long customerId) {
    List<Order> orderList;
    if(customerId == null)
      orderList = orderRepository.findAll();
    else
      orderList = orderRepository.findByCustomerId(customerId);
    return orderList.stream()
            .map(OrderDTO::fromOrder)
            .toList();
  }

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
      if(item.productId() == null)
        throw new OrderInvalidRequestDataException("Product id cannot be null");
      if(item.quantity() == null || item.quantity() <= 0)
        throw new OrderInvalidRequestDataException("Item quantity cannot be less than 0.");
      OrderItem orderItem = new OrderItem();
      orderItem.setQuantity(item.quantity());
      orderItem.setProductId(item.productId());
      order.addOrderItem(orderItem);
    }
    return OrderDTO.fromOrder(orderRepository.save(order));
  }
}
