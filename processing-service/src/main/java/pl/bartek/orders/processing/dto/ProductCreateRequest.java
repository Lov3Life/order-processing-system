package pl.bartek.orders.processing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductCreateRequest(
        @NotBlank
        String name,

        @NotNull
        @PositiveOrZero
        Integer stockQuantity) {
  public ProductCreateRequest {
    if(name != null){
      name = name.strip();
    }
  }
}
