package pl.bartek.orders.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.bartek.orders.order.entity.Customer;

public record CustomerCreateRequest(
        @Size(min = 2, max = 100)
        @NotBlank
        String firstName,

        @Size(min = 2, max = 100)
        @NotBlank
        String lastName,

        @Size(max = 255)
        @NotBlank
        @Email
        String email) {

  public CustomerCreateRequest {
    if (firstName != null)
      firstName = firstName.strip();
    if (lastName != null)
      lastName = lastName.strip();
    if (email != null)
      email = email.strip();
  }

  public static Customer toCustomer(CustomerCreateRequest request) {
    return new Customer(request.firstName, request.lastName, request.email);
  }
}
