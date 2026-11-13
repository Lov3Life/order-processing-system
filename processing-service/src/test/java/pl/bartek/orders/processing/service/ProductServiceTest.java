package pl.bartek.orders.processing.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.bartek.orders.processing.dto.ProductCreateRequest;
import pl.bartek.orders.processing.dto.ProductDTO;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.exception.ProductInvalidRequestDataException;
import pl.bartek.orders.processing.exception.ProductNotFoundException;
import pl.bartek.orders.processing.exception.ProductNotUniqueNameException;
import pl.bartek.orders.processing.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

  @Mock
  private ProductRepository productRepository;

  private ProductService productService;

  @BeforeEach
  void setup() {
    productService = new ProductService(productRepository);
  }

  @Test
  void shouldGetAllProducts() {
    Product product1 = new Product("24-inch monitor", 3);
    ReflectionTestUtils.setField(product1, "id", 1L);
    Product product2 = new Product("27-inch monitor", 6);
    ReflectionTestUtils.setField(product2, "id", 2L);
    when(productRepository.findAll())
            .thenReturn(List.of(product1, product2));
    List<ProductDTO> response = productService.getAllProducts();
    Assertions.assertNotNull(response);
    Assertions.assertEquals(2, response.size());
    Assertions.assertEquals(1L, response.getFirst().id());
    Assertions.assertEquals("24-inch monitor", response.getFirst().name());
    Assertions.assertEquals(3, response.getFirst().stockQuantity());
    Assertions.assertEquals(2L, response.getLast().id());
    Assertions.assertEquals("27-inch monitor", response.getLast().name());
    Assertions.assertEquals(6, response.getLast().stockQuantity());
    verify(productRepository).findAll();
  }

  @Test
  void shouldReturnAllProductsEmptyList() {
    when(productRepository.findAll())
            .thenReturn(List.of());
    List<ProductDTO> response = productService.getAllProducts();
    Assertions.assertNotNull(response);
    Assertions.assertEquals(0, response.size());
    verify(productRepository).findAll();
  }

  @Test
  void shouldGetProductById() {
    Product product1 = new Product("24-inch monitor", 3);
    ReflectionTestUtils.setField(product1, "id", 1L);
    when(productRepository.findById(1L))
            .thenReturn(Optional.of(product1));
    ProductDTO response = productService.getProduct(1L);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(1L, response.id());
    Assertions.assertEquals("24-inch monitor", response.name());
    Assertions.assertEquals(3, response.stockQuantity());
    verify(productRepository).findById(1L);
  }

  @Test
  void shouldThrowProductNotFoundException() {
    when(productRepository.findById(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(ProductNotFoundException.class,  () -> productService.getProduct(1L));
    verify(productRepository).findById(1L);
  }

  @Test
  void shouldAddProduct(){
    ProductCreateRequest request = new ProductCreateRequest("24-inch monitor", 3);
    when(productRepository.findByNameIgnoreCase("24-inch monitor"))
            .thenReturn(Optional.empty());
    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> {
              Product product = invocation.getArgument(0);
              ReflectionTestUtils.setField(product, "id", 1L);
              return product;
            });
    ProductDTO response = productService.addProduct(request);
    ProductDTO expected = new ProductDTO(1L, "24-inch monitor", 3);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(expected, response);
    verify(productRepository).findByNameIgnoreCase("24-inch monitor");
    verify(productRepository).save(argThat(product ->
            product.getName().equals("24-inch monitor") &&
            product.getStockQuantity() == 3
    ));
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void shouldNotAddProductWhenStockQuantityIsNegative() {
    ProductCreateRequest request = new ProductCreateRequest("24-inch monitor", -1);
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.addProduct(request));
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldNotAddProductWithSameName(){
    ProductCreateRequest request = new ProductCreateRequest("24-inch monitor", 3);
    when(productRepository.findByNameIgnoreCase("24-inch monitor"))
            .thenReturn(Optional.of(new Product("24-inch monitor", 3)));
    Assertions.assertThrows(ProductNotUniqueNameException.class,
            () -> productService.addProduct(request));
    verify(productRepository).findByNameIgnoreCase(request.name());
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldNotAddProductWhenStockQuantityIsNull() {
    ProductCreateRequest request = new ProductCreateRequest("24-inch monitor", null);
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.addProduct(request));
    verify(productRepository, never()).save(any(Product.class));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n", "\r"})
  void shouldNotAddProductWhenNameIsNull(String name) {
    ProductCreateRequest request = new ProductCreateRequest(name, 3);
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.addProduct(request));
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldAddStockQuantityToProduct(){
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", 3);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    ProductDTO response = productService.addStockQuantityToProduct(1L, 3);
    ProductDTO expected = new ProductDTO(1L, "24-inch monitor", 6);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(expected, response);
  }

  @Test
  void shouldAddStockQuantityEqualToIntegerMaxValue(){
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", Integer.MAX_VALUE - 1);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    ProductDTO response = productService.addStockQuantityToProduct(1L, 1);
    ProductDTO expected = new ProductDTO(1L, "24-inch monitor", Integer.MAX_VALUE);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(expected, response);
  }

  @Test
  void shouldThrowExceptionWhenStockAdditionOverflows(){
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", Integer.MAX_VALUE);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.addStockQuantityToProduct(1L, 1));
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldThrowProductNotFoundExceptionWhenAddingStock(){
    when(productRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(ProductNotFoundException.class,
            () -> productService.addStockQuantityToProduct(1L, 1));
    verify(productRepository, never()).save(any(Product.class));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {-2, -1, 0})
  void shouldThrowProductInvalidRequestDataExceptionWhenQuantityIsLessOrEqualsZeroWhenAdding(Integer quantity){
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", 3);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.addStockQuantityToProduct(1L, quantity));
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldSubtractStockQuantityToProduct(){
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", 3);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    ProductDTO response = productService.subtractStockQuantityToProduct(1L, 3);
    ProductDTO expected = new ProductDTO(1L, "24-inch monitor", 0);
    Assertions.assertNotNull(response);
    Assertions.assertEquals(expected, response);
  }

  @Test
  void shouldThrowProductNotFoundExceptionWhenSubtractingStock(){
    when(productRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.empty());
    Assertions.assertThrows(ProductNotFoundException.class,
            () -> productService.subtractStockQuantityToProduct(1L, 1));
    verify(productRepository, never()).save(any(Product.class));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {-2, -1, 0})
  void shouldThrowProductInvalidRequestDataExceptionWhenQuantityIsLessOrEqualsZeroWhenSubtracting(Integer quantity){
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", 3);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.subtractStockQuantityToProduct(1L, quantity));
    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void shouldThrowProductInvalidRequestDataExceptionWhenNewStockQuantityValueWillBeLessThanZero(){
    int startQuantity = 3;
    when(productRepository.findByIdForUpdate(1L))
            .thenAnswer(invocation -> {
              Product product = new Product("24-inch monitor", startQuantity);
              ReflectionTestUtils.setField(product, "id", 1L);
              return Optional.of(product);
            });
    Assertions.assertThrows(ProductInvalidRequestDataException.class,
            () -> productService.subtractStockQuantityToProduct(1L, startQuantity + 1));
    verify(productRepository, never()).save(any(Product.class));
  }
}
