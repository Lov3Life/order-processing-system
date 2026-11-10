package pl.bartek.orders.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemCreateRequest(
        @NotNull
        @Positive
        Long productId,

        @NotNull
        @Positive
        Integer quantity) {
}
