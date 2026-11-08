package pl.bartek.orders.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.bartek.orders.order.entity.Customer;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  Optional<Customer> findByEmail(String email);
}
