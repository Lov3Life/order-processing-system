package pl.bartek.orders.processing.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pl.bartek.orders.processing.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findByIdForUpdate(Long id);

  Optional<Product> findByNameIgnoreCase(String name);
}
