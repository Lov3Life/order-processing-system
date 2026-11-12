package pl.bartek.orders.processing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.bartek.orders.processing.dto.ProductCreateRequest;
import pl.bartek.orders.processing.dto.ProductDTO;
import pl.bartek.orders.processing.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  public List<ProductDTO> getProducts() {
    return productService.getAllProducts();
  }

  @GetMapping("/{id}")
  public ProductDTO getProduct(@PathVariable long id) {
    return productService.getProduct(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProductDTO addProduct(@RequestBody @Valid ProductCreateRequest request) {
    return productService.addProduct(request);
  }

  @PatchMapping("/addStockQuantity/{id}")
  public ProductDTO addStockQuantityToProduct(@PathVariable long id,
                                              @RequestParam @Positive int quantity) {
    return productService.addStockQuantityToProduct(id, quantity);
  }

  @PatchMapping("/subtractStockQuantity/{id}")
  public ProductDTO subtractStockQuantityToProduct(@PathVariable long id,
                                                   @RequestParam @Positive int quantity) {
    return productService.subtractStockQuantityToProduct(id, quantity);
  }
}
