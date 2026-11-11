package pl.bartek.orders.processing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.bartek.orders.processing.entity.Product;

public interface ProductRepository extends JpaRepository<Product,Long> {
}
