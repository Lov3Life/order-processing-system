package pl.bartek.orders.order.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.order.dto.OrderCreateRequest;
import pl.bartek.orders.order.dto.OrderDTO;
import pl.bartek.orders.order.dto.OrderItemCreateRequest;
import pl.bartek.orders.order.dto.OrderSummaryDTO;
import pl.bartek.orders.order.entity.Customer;
import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.entity.OrderItem;
import pl.bartek.orders.order.enums.OrderStatus;
import pl.bartek.orders.order.exception.OrderAlreadyProcessedException;
import pl.bartek.orders.order.exception.OrderInvalidRequestDataException;
import pl.bartek.orders.order.exception.OrderNotFoundException;
import pl.bartek.orders.order.repository.CustomerRepository;
import pl.bartek.orders.order.repository.OrderRepository;
import pl.bartek.orders.outbox.service.OutboxService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private CustomerRepository customerRepository;

  @Mock
  private OutboxService outboxService;

  private OrderService orderService;

  @BeforeEach
  void setup() {
    orderService = new OrderService(orderRepository, customerRepository, outboxService);
  }

  @Test
  void shouldGetAllOrders() {
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    Order order1 = new Order();
    order1.setOrderDate(Instant.now());
    order1.setOrderStatus(OrderStatus.PENDING);
    order1.setCustomer(customer);
    order1.addOrderItem(new OrderItem());
    order1.addOrderItem(new OrderItem());
    ReflectionTestUtils.setField(order1, "id", 1L);
    Order order2 = new Order();
    order2.setOrderDate(Instant.now());
    order2.setOrderStatus(OrderStatus.PENDING);
    order2.setCustomer(customer);
    order2.addOrderItem(new OrderItem());
    ReflectionTestUtils.setField(order2, "id", 2L);
    when(orderRepository.findAll())
            .thenReturn(List.of(order1, order2));
    List<OrderSummaryDTO> response = orderService.getOrders(null);
    Assertions.assertNotNull(response);
    Assertions.assertFalse(response.isEmpty());
    Assertions.assertEquals(2, response.size());
    Assertions.assertEquals(1L, response.getFirst().id());
    Assertions.assertNotNull(response.getFirst().customerId());
    Assertions.assertEquals(2L, response.getLast().id());
    Assertions.assertNotNull(response.getLast().customerId());
    verify(orderRepository).findAll();
  }

  @Test
  void shouldGetAllOrdersWhenEmpty() {
    when(orderRepository.findAll())
            .thenReturn(List.of());
    List<OrderSummaryDTO> response = orderService.getOrders(null);
    Assertions.assertNotNull(response);
    Assertions.assertTrue(response.isEmpty());
    verify(orderRepository).findAll();
  }

  @Test
  void shouldGetAllOrdersForCustomer() {
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    Order order1 = new Order();
    order1.setOrderDate(Instant.now());
    order1.setOrderStatus(OrderStatus.PENDING);
    order1.setCustomer(customer);
    order1.addOrderItem(new OrderItem());
    order1.addOrderItem(new OrderItem());
    ReflectionTestUtils.setField(order1, "id", 1L);
    Order order2 = new Order();
    order2.setOrderDate(Instant.now());
    order2.setOrderStatus(OrderStatus.PENDING);
    order2.setCustomer(customer);
    order2.addOrderItem(new OrderItem());
    ReflectionTestUtils.setField(order2, "id", 2L);
    when(orderRepository.findByCustomerId(customer.getId()))
            .thenReturn(List.of(order1, order2));
    List<OrderSummaryDTO> response = orderService.getOrders(customer.getId());
    Assertions.assertNotNull(response);
    Assertions.assertFalse(response.isEmpty());
    Assertions.assertEquals(2, response.size());
    Assertions.assertEquals(1L, response.getFirst().id());
    Assertions.assertNotNull(response.getFirst().customerId());
    Assertions.assertEquals(2L, response.getLast().id());
    Assertions.assertNotNull(response.getLast().customerId());
    verify(orderRepository).findByCustomerId(customer.getId());
  }

  @Test
  void shouldGetAllOrdersForCustomerWhenEmpty() {
    when(orderRepository.findByCustomerId(1L))
            .thenReturn(List.of());
    List<OrderSummaryDTO> response = orderService.getOrders(1L);
    Assertions.assertNotNull(response);
    Assertions.assertTrue(response.isEmpty());
    verify(orderRepository).findByCustomerId(1L);
  }

  @Test
  void shouldGetOrderById() {
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    Order order = new Order();
    order.setOrderDate(Instant.now());
    order.setOrderStatus(OrderStatus.PENDING);
    order.setCustomer(customer);
    order.addOrderItem(new OrderItem());
    order.addOrderItem(new OrderItem());
    ReflectionTestUtils.setField(order, "id", 1L);
    when(orderRepository.findById(1L))
            .thenReturn(Optional.of(order));
    OrderDTO response = orderService.getOrder(1L);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(1L, response.id());
    Assertions.assertEquals(1L, response.customerId());
    verify(orderRepository).findById(1L);
  }

  @Test
  void shouldThrowOrderNotFoundExceptionWhenOrderNotFound() {
    when(orderRepository.findById(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(1L));
    verify(orderRepository).findById(1L);
  }

  @Test
  void shouldCreateOrder() {
    List<OrderItemCreateRequest> items = List.of(
            new OrderItemCreateRequest(1L, 10),
            new OrderItemCreateRequest(4L, 2)
    );
    OrderCreateRequest request = new OrderCreateRequest(1L, items);
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> {
              Order order = invocation.getArgument(0);
              ReflectionTestUtils.setField(order, "id", 1L);
              ReflectionTestUtils.setField(order.getOrderItems().getFirst(), "id", 1L);
              ReflectionTestUtils.setField(order.getOrderItems().getLast(), "id", 2L);
              return order;
            });
    OrderDTO response = orderService.createOrder(request);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(1L, response.id());
    Assertions.assertEquals(1L, response.customerId());
    Assertions.assertEquals(2, response.items().size());
    verify(orderRepository).save(argThat(order ->
            order.getId().equals(1L) &&
            order.getOrderDate() != null &&
            order.getOrderStatus().equals(OrderStatus.PENDING) &&
            order.getCustomer().getId().equals(1L) &&
            order.getOrderItems().size() == 2 &&
            order.getOrderItems().getFirst().getProductId().equals(1L) &&
            order.getOrderItems().getLast().getProductId().equals(4L) &&
            order.getOrderItems().getFirst().getQuantity() == 10 &&
            order.getOrderItems().getLast().getQuantity() == 2
    ));
  }

  @Test
  void shouldNotCreateOrderWhenCustomerIdIsNull() {
    OrderCreateRequest request = new OrderCreateRequest(null, List.of(new OrderItemCreateRequest(1L, 2)));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(customerRepository, never()).findById(anyLong());
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotCreateOrderWhenItemsListIsNull() {
    OrderCreateRequest request = new OrderCreateRequest(1L, null);
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotCreateOrderWhenItemsListIsEmpty() {
    OrderCreateRequest request = new OrderCreateRequest(1L, List.of());
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotCreateOrderWhenOrderItemsAreNotUnique() {
    OrderItemCreateRequest item1 = new OrderItemCreateRequest(1L, 2);
    OrderItemCreateRequest item2 = new OrderItemCreateRequest(1L, 5);
    OrderCreateRequest request = new OrderCreateRequest(1L, List.of(item1, item2));
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotCreateOrderWhenOrderItemIdIsNull() {
    OrderItemCreateRequest item1 = new OrderItemCreateRequest(null, 2);
    OrderCreateRequest request = new OrderCreateRequest(1L, List.of(item1));
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotCreateOrderWhenOrderItemQuantityIsNull() {
    OrderItemCreateRequest item1 = new OrderItemCreateRequest(1L, null);
    OrderCreateRequest request = new OrderCreateRequest(1L, List.of(item1));
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void shouldNotCreateOrderWhenOrderItemQuantityIsLessThanZero() {
    OrderItemCreateRequest item1 = new OrderItemCreateRequest(1L, -2);
    OrderCreateRequest request = new OrderCreateRequest(1L, List.of(item1));
    Customer customer = new Customer();
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    Assertions.assertThrows(OrderInvalidRequestDataException.class, () -> orderService.createOrder(request));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACCEPTED", "REJECTED"})
  void shouldChangeOrderStatusFromPendingToValidInput(String status) {
    ProcessingStatus newStatus = ProcessingStatus.valueOf(status);
    Order order = new Order();
    order.setOrderStatus(OrderStatus.PENDING);
    when(orderRepository.findById(1L))
            .thenReturn(Optional.of(order));
    orderService.changeOrderStatus(1L, newStatus);
    Assertions.assertNotEquals(OrderStatus.PENDING, order.getOrderStatus());
    Assertions.assertEquals(OrderStatus.valueOf(status.toUpperCase()), order.getOrderStatus());
    verify(orderRepository).save(order);
  }

  @Test
  void shouldThrowOrderNotFoundExceptionWhenChangingStatus() {
    when(orderRepository.findById(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(OrderNotFoundException.class,
            () -> orderService.changeOrderStatus(1L, ProcessingStatus.ACCEPTED));
    verify(orderRepository).findById(1L);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACCEPTED","REJECTED"})
  void shouldNotChangeOrderStatusWhenInputIsTheSameLikeOrderStatus(String status) {
    ProcessingStatus newStatus = ProcessingStatus.valueOf(status);
    Order order = new Order();
    order.setOrderStatus(OrderStatus.valueOf(status.toUpperCase()));
    when(orderRepository.findById(1L))
            .thenReturn(Optional.of(order));
    orderService.changeOrderStatus(1L, newStatus);
    Assertions.assertEquals(OrderStatus.valueOf(status.toUpperCase()), order.getOrderStatus());
    verify(orderRepository, never()).save(any(Order.class));
  }

  @ParameterizedTest
  @CsvSource({"ACCEPTED, REJECTED", "REJECTED, ACCEPTED"})
  void shouldThrowOrderAlreadyProcessedExceptionWhenOrderHasAlreadyBeenProcessedAndHasDifferenceStatus(
          String orderStatus, String processedStatus) {
    ProcessingStatus newStatus = ProcessingStatus.valueOf(processedStatus);
    Order order = new Order();
    ReflectionTestUtils.setField(order, "id", 1L);
    order.setOrderStatus(OrderStatus.valueOf(orderStatus));
    when(orderRepository.findById(1L))
            .thenReturn(Optional.of(order));
    Assertions.assertThrows(OrderAlreadyProcessedException.class,
            () -> orderService.changeOrderStatus(1L, newStatus));
    verify(orderRepository, never()).save(any(Order.class));
  }
}
