package pl.bartek.orders.processing.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.bartek.orders.processing.dto.ProductCreateRequest;
import pl.bartek.orders.processing.dto.ProductDTO;
import pl.bartek.orders.processing.exception.ProductInvalidRequestDataException;
import pl.bartek.orders.processing.exception.ProductNotFoundException;
import pl.bartek.orders.processing.service.ProductService;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
public class ProductControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProductService productService;

  static String addProductRequestBuilder(String name, Integer stockQuantity) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("stockQuantity", stockQuantity);
    return mapper.writeValueAsString(map);
  }

  static Stream<String> invalidAddProductRequest() {
    return Stream.of(
            addProductRequestBuilder(null, 1),
            addProductRequestBuilder("", 1),
            addProductRequestBuilder("   ", 1),
            addProductRequestBuilder("\n", 1),
            addProductRequestBuilder("\t", 1),
            addProductRequestBuilder("\r", 1),
            addProductRequestBuilder("24-inches monitor", null),
            addProductRequestBuilder("24-inches monitor", -1));
  }

  @Test
  void shouldReturn200AllProducts() throws Exception {
    ProductDTO productDTO1 = new ProductDTO(1L, "24-inches monitor", 3);
    ProductDTO productDTO2 = new ProductDTO(2L, "27-inches monitor", 8);
    when(productService.getAllProducts())
            .thenReturn(Arrays.asList(productDTO1, productDTO2));
    mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(1L))
            .andExpect(jsonPath("$[0].name").value("24-inches monitor"))
            .andExpect(jsonPath("$[0].stockQuantity").value(3))
            .andExpect(jsonPath("$[1].id").value(2L))
            .andExpect(jsonPath("$[1].name").value("27-inches monitor"))
            .andExpect(jsonPath("$[1].stockQuantity").value(8));
    verify(productService).getAllProducts();
  }

  @Test
  void shouldReturn200AllProductsEmptyList() throws Exception {
    when(productService.getAllProducts())
            .thenReturn(List.of());
    mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    verify(productService).getAllProducts();
  }

  @Test
  void shouldReturn200ProductById() throws Exception {
    ProductDTO productDTO = new ProductDTO(1L, "24-inches monitor", 3);
    when(productService.getProduct(1L))
            .thenReturn(productDTO);
    mockMvc.perform(get("/api/products/{id}", 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
    verify(productService).getProduct(1L);
  }

  @Test
  void shouldReturn404WhenThrowProductNotFoundExceptionWhenSearchingById() throws Exception {
    when(productService.getProduct(1L))
            .thenThrow(new ProductNotFoundException(1L));
    mockMvc.perform(get("/api/products/{id}", 1))
            .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn201WhenAddProduct() throws Exception {
    String request = """
            {
              "name": "24-inches monitor",
              "stockQuantity": "3"
            }
            """;
    ProductDTO expected = new ProductDTO(1L, "24-inches monitor", 3);
    when(productService.addProduct(any(ProductCreateRequest.class)))
            .thenReturn(expected);
    mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
    verify(productService).addProduct(any(ProductCreateRequest.class));
  }

  @ParameterizedTest
  @MethodSource("invalidAddProductRequest")
  void shouldReturn404WhenRequestIsInvalidWhenAddProduct(String request) throws Exception {
    mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest());
    verify(productService, never()).addProduct(any(ProductCreateRequest.class));
  }

  @Test
  void shouldReturn404WhenThrowProductInvalidRequestDataException() throws Exception {
    String request = """
            {
              "name": "24-inches monitor",
              "stockQuantity": "3"
            }
            """;
    when(productService.addProduct(any(ProductCreateRequest.class)))
            .thenThrow(new ProductInvalidRequestDataException("Invalid request"));
    mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn200WhenAddStockQuantityToProduct() throws Exception {
    ProductDTO productDTO = new ProductDTO(1L, "24-inches monitor", 3);
    when(productService.addStockQuantityToProduct(1L, 3))
            .thenReturn(productDTO);
    mockMvc.perform(patch("/api/products/addStockQuantity/{id}", 1)
                    .param("quantity", String.valueOf(3))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
    verify(productService).addStockQuantityToProduct(1L, 3);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void shouldReturn400WhenAddStockQuantityToProductInvalidRequest(int quantity) throws Exception {
    mockMvc.perform(patch("/api/products/addStockQuantity/{id}", 1)
                    .param("quantity", String.valueOf(quantity))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenProductInvalidRequestDataExceptionAddStockQuantity() throws Exception {
    when(productService.addStockQuantityToProduct(1L, 2))
            .thenThrow(new ProductInvalidRequestDataException("Invalid request"));
    mockMvc.perform(patch("/api/products/addStockQuantity/{id}", 1)
                    .param("quantity", String.valueOf(2))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn200WhenSubtractStockQuantityToProduct() throws Exception {
    ProductDTO productDTO = new ProductDTO(1L, "24-inches monitor", 3);
    when(productService.subtractStockQuantityToProduct(1L, 3))
            .thenReturn(productDTO);
    mockMvc.perform(patch("/api/products/subtractStockQuantity/{id}", 1)
                    .param("quantity", String.valueOf(3))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("24-inches monitor"))
            .andExpect(jsonPath("$.stockQuantity").value(3));
    verify(productService).subtractStockQuantityToProduct(1L, 3);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void shouldReturn400WhenSubtractStockQuantityToProductInvalidRequest(int quantity) throws Exception {
    mockMvc.perform(patch("/api/products/subtractStockQuantity/{id}", 1)
                    .param("quantity", String.valueOf(quantity))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenProductInvalidRequestDataExceptionWhenSubtractStockQuantity() throws Exception {
    when(productService.subtractStockQuantityToProduct(1L, 2))
            .thenThrow(new ProductInvalidRequestDataException("Invalid request"));
    mockMvc.perform(patch("/api/products/subtractStockQuantity/{id}", 1)
                    .param("quantity", String.valueOf(2))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
  }
}
