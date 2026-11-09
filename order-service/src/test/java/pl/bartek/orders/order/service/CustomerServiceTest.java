package pl.bartek.orders.order.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.bartek.orders.order.dto.CustomerCreateRequest;
import pl.bartek.orders.order.dto.CustomerDTO;
import pl.bartek.orders.order.entity.Customer;
import pl.bartek.orders.order.exception.CustomerNotFoundException;
import pl.bartek.orders.order.exception.CustomerNotUniqueEmailException;
import pl.bartek.orders.order.repository.CustomerRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock
  private CustomerRepository customerRepository;

  @InjectMocks
  private CustomerService customerService;

  @BeforeEach
  void setUp() {
    customerService = new CustomerService(customerRepository);
  }

  @Test
  void shouldGetAllCustomers() {
    Customer customer1 = new Customer("Matt", "Murdock", "matt.murdock@example.com");
    Customer customer2 = new Customer("Danny", "Rand", "danny.rand@rand.corporation.com");
    when(customerRepository.findAll())
            .thenReturn(List.of(customer1, customer2));
    List<CustomerDTO> response = customerService.getAllCustomers();
    Assertions.assertNotNull(response);
    Assertions.assertEquals(2, response.size());
    verify(customerRepository).findAll();
  }

  @Test
  void shouldGetEmptyAllCustomers() {
    when(customerRepository.findAll())
            .thenReturn(List.of());
    List<CustomerDTO> response = customerService.getAllCustomers();
    Assertions.assertNotNull(response);
    Assertions.assertEquals(0, response.size());
    verify(customerRepository).findAll();
  }

  @Test
  void shouldGetCustomerById() {
    Customer customer = new Customer("Matt", "Murdock", "matt.murdock@example.com");
    ReflectionTestUtils.setField(customer, "id", 1L);
    when(customerRepository.findById(1L))
            .thenReturn(Optional.of(customer));
    CustomerDTO response = customerService.getCustomer(1L);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(CustomerDTO.fromCustomer(customer), response);
    verify(customerRepository).findById(1L);
  }

  @Test
  void shouldThrowCustomerNotFoundException() {
    when(customerRepository.findById(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(CustomerNotFoundException.class, () -> customerService.getCustomer(1L));
    verify(customerRepository).findById(1L);
  }

  @Test
  void shouldCreateCustomer() {
    CustomerCreateRequest request = new CustomerCreateRequest("Matt", "Murdock", "matt.murdock@example.com");
    when(customerRepository.findByEmail(request.email()))
            .thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class)))
            .thenAnswer(i -> i.getArgument(0));
    CustomerDTO customerDTO = customerService.createCustomer(request);
    Assertions.assertNotNull(customerDTO);
    Assertions.assertEquals(request.firstName(), customerDTO.firstName());
    verify(customerRepository).findByEmail(request.email());
  }

  @Test
  void shouldThrowCustomerNotUniqueEmailExceptionWhenEmailAlreadyExists() {
    CustomerCreateRequest request = new CustomerCreateRequest("Matt", "Murdock", "matt.murdock@example.com");
    when(customerRepository.findByEmail(request.email()))
            .thenReturn(Optional.of(new Customer("name", "name", "matt.murdock@example.com")));
    Assertions.assertThrows(CustomerNotUniqueEmailException.class, () -> customerService.createCustomer(request));
    verify(customerRepository).findByEmail(request.email());
  }
}