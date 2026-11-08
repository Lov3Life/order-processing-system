package pl.bartek.orders.order.dto;

import pl.bartek.orders.order.entity.Customer;

public record CustomerDTO(
        Long id,
        String firstName,
        String lastName,
        String email
) {
  public static CustomerDTO fromCustomer(Customer customer) {
    return new CustomerDTO(
            customer.getId(),
            customer.getFirstName(),
            customer.getLastName(),
            customer.getEmail());
  }
}
