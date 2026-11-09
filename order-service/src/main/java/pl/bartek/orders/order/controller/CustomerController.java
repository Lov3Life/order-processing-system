package pl.bartek.orders.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.bartek.orders.order.dto.CustomerCreateRequest;
import pl.bartek.orders.order.dto.CustomerDTO;
import pl.bartek.orders.order.service.CustomerService;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  public List<CustomerDTO> getCustomers() {
    return customerService.getAllCustomers();
  }

  @GetMapping("/{id}")
  public CustomerDTO getCustomerById(@PathVariable long id) {
    return customerService.getCustomer(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerDTO createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
    return customerService.createCustomer(request);
  }
}
