package pl.bartek.orders.processing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bartek.orders.processing.dto.ProductCreateRequest;
import pl.bartek.orders.processing.dto.ProductDTO;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.exception.ProductInvalidRequestDataException;
import pl.bartek.orders.processing.exception.ProductNotFoundException;
import pl.bartek.orders.processing.repository.ProductRepository;

import java.util.List;

@Service
public class ProductService {

  private final ProductRepository productRepository;

  public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  public ProductDTO getProduct(Long id) {
    return productRepository.findById(id)
            .map(ProductDTO::fromProduct)
            .orElseThrow(() -> new ProductNotFoundException(id));
  }

  public List<ProductDTO> getAllProducts() {
    return productRepository.findAll().stream()
            .map(ProductDTO::fromProduct)
            .toList();
  }

  @Transactional
  public ProductDTO addProduct(ProductCreateRequest request) {
    if(request.stockQuantity() == null || request.stockQuantity() < 0)
      throw new ProductInvalidRequestDataException("Stock quantity cannot be negative.");
    if(request.name() == null || request.name().isBlank())
      throw new ProductInvalidRequestDataException("Name cannot be empty.");
    if(productRepository.findByNameIgnoreCase(request.name()).isPresent())
      throw new ProductInvalidRequestDataException("Product with the same name already exists.");
    Product product = new Product();
    product.setName(request.name());
    product.setStockQuantity(request.stockQuantity());
    return ProductDTO.fromProduct(productRepository.save(product));
  }

  @Transactional
  public ProductDTO addStockQuantityToProduct(Long id, Integer quantity) {
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    if(quantity == null || quantity <= 0)
      throw new ProductInvalidRequestDataException("Stock quantity to add must be greater than zero.");
    product.addStockQuantity(quantity);
    return ProductDTO.fromProduct(productRepository.save(product));
  }

  @Transactional
  public ProductDTO subtractStockQuantityToProduct(Long id, Integer quantity) {
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    if(quantity == null || quantity <= 0)
      throw new ProductInvalidRequestDataException("Stock quantity to subtract must be greater than zero.");
    int newStockQuantity = product.getStockQuantity() - quantity;
    if(newStockQuantity < 0)
      throw new ProductInvalidRequestDataException("New stock quantity cannot be less than zero.");
    product.setStockQuantity(newStockQuantity);
    return ProductDTO.fromProduct(productRepository.save(product));
  }
}
