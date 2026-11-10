package pl.bartek.orders.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.order.dto.CustomerCreateRequest;
import pl.bartek.orders.order.dto.CustomerDTO;
import pl.bartek.orders.order.entity.Customer;
import pl.bartek.orders.order.exception.CustomerNotFoundException;
import pl.bartek.orders.order.exception.CustomerNotUniqueEmailException;
import pl.bartek.orders.order.repository.CustomerRepository;

import java.util.List;

@Service
public class CustomerService {

  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public List<CustomerDTO> getAllCustomers() {
    return customerRepository.findAll().stream()
            .map(CustomerDTO::fromCustomer)
            .toList();
  }

  public CustomerDTO getCustomer(Long id) {
    return customerRepository.findById(id)
            .map(CustomerDTO::fromCustomer)
            .orElseThrow(() -> new CustomerNotFoundException(id));
  }


  @Transactional
  public CustomerDTO createCustomer(CustomerCreateRequest request) {
    if(customerRepository.findByEmail(request.email()).isPresent()) {
      throw new CustomerNotUniqueEmailException("Customer with email " + request.email() + " already exists.");
    }
    Customer customer = CustomerCreateRequest.toCustomer(request);
    return CustomerDTO.fromCustomer(customerRepository.save(customer));
  }
}
