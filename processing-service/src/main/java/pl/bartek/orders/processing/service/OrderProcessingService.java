package pl.bartek.orders.processing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.event.OrderCreatedEvent;
import pl.bartek.orders.event.OrderItemEvent;
import pl.bartek.orders.event.ProcessingStatus;
import pl.bartek.orders.processing.dto.OrderProcessingResult;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.repository.ProductRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderProcessingService {

  private final ProductRepository productRepository;

  public OrderProcessingService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Transactional
  public OrderProcessingResult processOrder(OrderCreatedEvent order) {
    List<OrderItemEvent> items = new ArrayList<>(order.items());
    items.sort(Comparator.comparingLong(OrderItemEvent::productId));
    List<Product> products = new ArrayList<>();
    for(OrderItemEvent item : items) {
      Product product = productRepository.findByIdForUpdate(item.productId())
              .orElse(null);
      if(product == null)
        return new OrderProcessingResult(ProcessingStatus.REJECTED,
                "product with id " + item.productId() + " not found");
      products.add(product);
      if(product.getStockQuantity() - item.quantity() < 0)
        return new OrderProcessingResult(ProcessingStatus.REJECTED,
                "Insufficient quantity(" + item.quantity() + ") of the product with ID: " + item.productId());
    }
    for(int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      product.subStockQuantity(items.get(i).quantity());
      productRepository.save(product);
    }
    return new OrderProcessingResult(ProcessingStatus.ACCEPTED, null);
  }
}
