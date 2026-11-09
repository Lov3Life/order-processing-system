package pl.bartek.orders.order.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.bartek.orders.order.dto.CustomerCreateRequest;
import pl.bartek.orders.order.dto.CustomerDTO;
import pl.bartek.orders.order.exception.CustomerNotFoundException;
import pl.bartek.orders.order.exception.CustomerNotUniqueEmailException;
import pl.bartek.orders.order.service.CustomerService;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
public class CustomerControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CustomerService customerService;

  @Autowired
  private ObjectMapper objectMapper;

  static Stream<String> allJavaWhitespaces() {
    return IntStream.rangeClosed(0, Character.MAX_VALUE)
            .filter(Character::isWhitespace)
            .mapToObj(c -> String.valueOf((char) c));
  }

  static Stream<Arguments> allCreateRequestInvalidData() {
    return Stream.of(
            Arguments.of("M", "Murdock", "matt.murdock@example.com"),
            Arguments.of("    M    ", "Murdock", "matt.murdock@example.com"),
            Arguments.of("M".repeat(101), "Murdock", "matt.murdock@example.com"),
            Arguments.of("", "Murdock", "matt.murdock@example.com"),
            Arguments.of("Matt", "M", "matt.murdock@example.com"),
            Arguments.of("Matt", "   M   ", "matt.murdock@example.com"),
            Arguments.of("Matt", "M".repeat(101), "matt.murdock@example.com"),
            Arguments.of("Matt", "", "matt.murdock@example.com"),
            Arguments.of("Matt", "Murdock", "matt.murdockexample.com"),
            Arguments.of("Matt", "Murdock", "@example.com"),
            Arguments.of("Matt", "Murdock", "matt.murdock@.com"),
            Arguments.of("Matt", "Murdock", "matt.murdock@"),
            Arguments.of("Matt", "Murdock", "matt.murdock@example..com")
    );
  }

  @Test
  void shouldReturnAllCustomers() throws Exception {
    CustomerDTO customerDTO1 = new CustomerDTO(1L, "Matt", "Murdock", "matt.murdock@example.com");
    CustomerDTO customerDTO2 = new CustomerDTO(2L, "Danny", "Rand", "danny.rand@rand.corporation.com");
    when(customerService.getAllCustomers())
            .thenReturn(List.of(customerDTO1, customerDTO2));
    mockMvc.perform(get("/api/customers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].firstName").value("Matt"))
            .andExpect(jsonPath("$[1].id").value(2L))
            .andExpect(jsonPath("$[1].firstName").value("Danny"));
    verify(customerService).getAllCustomers();
  }

  @Test
  void shouldReturnEmptyListAllCustomers() throws Exception {
    when(customerService.getAllCustomers())
            .thenReturn(Collections.emptyList());
    mockMvc.perform(get("/api/customers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    verify(customerService).getAllCustomers();
  }

  @Test
  void shouldReturnCustomerById() throws Exception {
    CustomerDTO customerDTO = new CustomerDTO(1L, "Matt", "Murdock", "matt.murdock@example.com");
    when(customerService.getCustomer(1L))
            .thenReturn(customerDTO);
    mockMvc.perform(get("/api/customers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.firstName").value("Matt"))
            .andExpect(jsonPath("$.lastName").value("Murdock"))
            .andExpect(jsonPath("$.email").value("matt.murdock@example.com"));
    verify(customerService).getCustomer(1L);
  }

  @Test
  void shouldReturn404WhenCustomerNotFoundCustomerById() throws Exception {
    when(customerService.getCustomer(1L))
            .thenThrow(new CustomerNotFoundException(1L));
    mockMvc.perform(get("/api/customers/1"))
            .andExpect(status().isNotFound());
  }

  @Test
  void shouldCreateCustomer() throws Exception {
    String request = """
            {
              "firstName": "Matt",
              "lastName": "Murdock",
              "email": "matt.murdock@example.com"
            }
            """;
    CustomerDTO response = new CustomerDTO(1L, "Matt", "Murdock", "matt.murdock@example.com");
    when(customerService.createCustomer(any(CustomerCreateRequest.class)))
            .thenReturn(response);
    mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("Matt"))
            .andExpect(jsonPath("$.lastName").value("Murdock"))
            .andExpect(jsonPath("$.email").value("matt.murdock@example.com"));
    verify(customerService).createCustomer(any(CustomerCreateRequest.class));
  }

  @ParameterizedTest
  @MethodSource("allJavaWhitespaces")
  void shouldCreateCustomerAndStripFields(String whitespace) throws Exception {
    Map<String, String> request = new HashMap<>();
    request.put("firstName", whitespace + "Matt" + whitespace);
    request.put("lastName", whitespace + "Murdock" + whitespace);
    request.put("email", whitespace + "matt.murdock@example.com" + whitespace);
    CustomerDTO response = new CustomerDTO(1L, "Matt", "Murdock", "matt.murdock@example.com");
    when(customerService.createCustomer(any(CustomerCreateRequest.class)))
            .thenReturn(response);
    mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    verify(customerService).createCustomer(argThat(requestCaptured ->
            requestCaptured.firstName().equals("Matt") &&
            requestCaptured.lastName().equals("Murdock") &&
            requestCaptured.email().equals("matt.murdock@example.com")
    ));
  }

  @ParameterizedTest
  @MethodSource("allCreateRequestInvalidData")
  void shouldNotCreateCustomerWhenInvalidData(String firstName, String lastName, String email) throws Exception {
    String request = """
            {
              "firstName": "%s",
              "lastName": "%s",
              "email": "%s"
            }
            """.formatted(firstName, lastName, email);
    mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest());
    verify(customerService, never()).createCustomer(any(CustomerCreateRequest.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {
          """
          {
            "firstName": "Matt",
            "lastName": "Murdock"
          }
          """,
          """
          {
            "firstName": "Matt",
            "email": "matt.murdock@example.com"
          }
          """,
          """
          {
            "lastName": "Murdock",
            "email": "matt.murdock@example.com"
          }
          """})
  void shouldNotCreateCustomerWhenValueMissing(String request) throws Exception {
    mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest());
    verify(customerService, never()).createCustomer(any(CustomerCreateRequest.class));
  }

  @Test
  void shouldNotCreateUserWhenEmailIsNotUniqueAndThrowCustomerNotUniqueEmailException() throws Exception {
    String request = """
            {
              "firstName": "Matt",
              "lastName": "Murdock",
              "email": "matt.murdock@example.com"
            }
            """;
    when(customerService.createCustomer(any(CustomerCreateRequest.class)))
            .thenThrow(new CustomerNotUniqueEmailException(
                    "Customer with email matt.murdock@example.com already exists."
            ));
    mockMvc.perform(post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Customer with email matt.murdock@example.com already exists."));
    verify(customerService).createCustomer(any(CustomerCreateRequest.class));
  }
}
