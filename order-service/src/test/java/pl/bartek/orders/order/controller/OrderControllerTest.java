package pl.bartek.orders.order.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.bartek.orders.order.dto.OrderCreateRequest;
import pl.bartek.orders.order.dto.OrderDTO;
import pl.bartek.orders.order.dto.OrderItemDTO;
import pl.bartek.orders.order.enums.OrderStatus;
import pl.bartek.orders.order.exception.CustomerNotFoundException;
import pl.bartek.orders.order.exception.OrderInvalidRequestDataException;
import pl.bartek.orders.order.exception.OrderNotFoundException;
import pl.bartek.orders.order.service.CustomerService;
import pl.bartek.orders.order.service.OrderService;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private CustomerService customerService;

  static String jsonRequestBuilder(Long customerId, Long[] productIds, Integer[] quantities) {
    List<Map<String, Number>> items = null;
    if(productIds != null && quantities != null) {
      items = new ArrayList<>();
      if(productIds.length != quantities.length)
        throw new IllegalArgumentException("Invalid tested data");
      for(int i = 0; i < productIds.length; i++) {
        Map<String, Number> item = new HashMap<>();
        item.put("productId", productIds[i]);
        item.put("quantity", quantities[i]);
        items.add(item);
      }
    }
    Map<String, Object> request = new HashMap<>();
    request.put("customerId", customerId);
    request.put("items", items);
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.writeValueAsString(request);
  }

  static Stream<String> allInvalidCreateDTOOptions() {
    return Stream.of(
            jsonRequestBuilder(null, new Long[]{1L}, new Integer[]{2}),
            jsonRequestBuilder(-1L, new Long[]{1L}, new Integer[]{2}),
            jsonRequestBuilder(0L, new Long[]{1L}, new Integer[]{2}),
            jsonRequestBuilder(1L, null, null),
            jsonRequestBuilder(1L, new Long[]{}, new Integer[]{}),
            jsonRequestBuilder(1L, new Long[]{-2L}, new Integer[]{2}),
            jsonRequestBuilder(1L, new Long[]{0L}, new Integer[]{2}),
            jsonRequestBuilder(1L, new Long[]{null}, new Integer[]{2}),
            jsonRequestBuilder(1L, new Long[]{1L}, new Integer[]{-2}),
            jsonRequestBuilder(1L, new Long[]{1L}, new Integer[]{0}),
            jsonRequestBuilder(1L, new Long[]{1L}, new Integer[]{null})
    );
  }

  @Test
  void shouldReturnAllOrders() throws Exception {
    Instant now = Instant.now();
    OrderItemDTO item = new OrderItemDTO(1L, 1L, 2L, 6);
    OrderDTO orderDTO = new OrderDTO(1L, 1L, OrderStatus.PENDING, now, List.of(item));
    when(orderService.getOrders(null))
            .thenReturn(List.of(orderDTO));
    mockMvc.perform(get("/api/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].orderStatus").value(OrderStatus.PENDING.toString()))
            .andExpect(jsonPath("$[0].customerId").value(1L))
            .andExpect(jsonPath("$[0].orderDate").value(now.toString()))
            .andExpect(jsonPath("$[0].items", hasSize(1)))
            .andExpect(jsonPath("$[0].items[0].id").value(1L))
            .andExpect(jsonPath("$[0].items[0].orderId").value(1L))
            .andExpect(jsonPath("$[0].items[0].productId").value(2L))
            .andExpect(jsonPath("$[0].items[0].quantity").value(6));
    verify(orderService).getOrders(null);
  }

  @Test
  void shouldReturnAllOrdersEmptyList() throws Exception {
    when(orderService.getOrders(null))
            .thenReturn(List.of());
    mockMvc.perform(get("/api/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    verify(orderService).getOrders(null);
  }

  @Test
  void shouldReturnAllOrdersByCustomerId() throws Exception {
    Instant now = Instant.now();
    OrderItemDTO item = new OrderItemDTO(1L, 1L, 2L, 6);
    OrderDTO orderDTO = new OrderDTO(1L, 1L, OrderStatus.PENDING, now, List.of(item));
    when(orderService.getOrders(1L))
            .thenReturn(List.of(orderDTO));
    mockMvc.perform(get("/api/orders")
                    .param("customerId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].orderStatus").value(OrderStatus.PENDING.toString()))
            .andExpect(jsonPath("$[0].customerId").value(1L))
            .andExpect(jsonPath("$[0].orderDate").value(now.toString()))
            .andExpect(jsonPath("$[0].items", hasSize(1)))
            .andExpect(jsonPath("$[0].items[0].id").value(1L))
            .andExpect(jsonPath("$[0].items[0].orderId").value(1L))
            .andExpect(jsonPath("$[0].items[0].productId").value(2L))
            .andExpect(jsonPath("$[0].items[0].quantity").value(6));
    verify(orderService).getOrders(1L);
  }

  @Test
  void shouldReturnOrder() throws Exception {
    Instant now = Instant.now();
    OrderItemDTO item = new OrderItemDTO(1L, 1L, 2L, 6);
    OrderDTO orderDTO = new OrderDTO(1L, 1L, OrderStatus.PENDING, now, List.of(item));
    when(orderService.getOrder(1L))
            .thenReturn(orderDTO);
    mockMvc.perform(get("/api/orders/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.orderStatus").value(OrderStatus.PENDING.toString()))
            .andExpect(jsonPath("$.customerId").value(1L))
            .andExpect(jsonPath("$.orderDate").value(now.toString()))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(1L))
            .andExpect(jsonPath("$.items[0].orderId").value(1L))
            .andExpect(jsonPath("$.items[0].productId").value(2L))
            .andExpect(jsonPath("$.items[0].quantity").value(6));
    verify(orderService).getOrder(1L);
  }

  @Test
  void shouldReturn404OrderNotFoundWhenOrderNotExist() throws Exception {
    when(orderService.getOrder(1L))
            .thenThrow(new OrderNotFoundException(1L));
    mockMvc.perform(get("/api/orders/{id}", 1L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Order with id 1 not found."));
    verify(orderService).getOrder(1L);
  }

  @Test
  void shouldCreateOrder() throws Exception {
    String request = """
            {
              "customerId": 1,
              "items": [
                {
                  "productId": 10,
                  "quantity": 2
                },
                {
                  "productId": 15,
                  "quantity": 3
                }
              ]
            }
            """;
    Instant now = Instant.now();
    OrderItemDTO item1 = new OrderItemDTO(1L, 1L, 10L, 2);
    OrderItemDTO item2 = new OrderItemDTO(2L, 1L, 15L, 3);
    OrderDTO order = new OrderDTO(1L, 1L, OrderStatus.PENDING, now, List.of(item1, item2));
    when(orderService.createOrder(any(OrderCreateRequest.class)))
            .thenReturn(order);
    mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.orderStatus").value(OrderStatus.PENDING.toString()))
            .andExpect(jsonPath("$.customerId").value(1L))
            .andExpect(jsonPath("$.orderDate").value(now.toString()))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].id").value(1L))
            .andExpect(jsonPath("$.items[0].orderId").value(1L))
            .andExpect(jsonPath("$.items[0].productId").value(10L))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[1].id").value(2L))
            .andExpect(jsonPath("$.items[1].orderId").value(1L))
            .andExpect(jsonPath("$.items[1].productId").value(15L))
            .andExpect(jsonPath("$.items[1].quantity").value(3));
    verify(orderService).createOrder(any(OrderCreateRequest.class));
  }

  @ParameterizedTest
  @MethodSource("allInvalidCreateDTOOptions")
  void shouldReturn404WithInvalidDTOData(String request) throws Exception {
    mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest());
    verify(orderService, never()).createOrder(any(OrderCreateRequest.class));
  }

  @Test
  void shouldReturn404WhenCustomerNotFoundWhenCreatingOrder() throws Exception {
    String request = jsonRequestBuilder(4L, new Long[]{2L}, new Integer[]{6});
    when(orderService.createOrder(any(OrderCreateRequest.class)))
            .thenThrow(new CustomerNotFoundException(4L));
    mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Customer with id 4 not found."));
    verify(orderService).createOrder(any(OrderCreateRequest.class));
  }

  @Test
  void shouldReturn404WhenDuplicatedItemWhenCreatingOrder() throws Exception {
    String request = jsonRequestBuilder(4L, new Long[]{2L, 2L}, new Integer[]{6, 2});
    when(orderService.createOrder(any(OrderCreateRequest.class)))
            .thenThrow(new OrderInvalidRequestDataException("Items should be unique"));
    mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Items should be unique"));
    verify(orderService).createOrder(any(OrderCreateRequest.class));
  }
}
