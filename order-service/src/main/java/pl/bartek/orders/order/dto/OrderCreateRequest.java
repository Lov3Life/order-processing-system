package pl.bartek.orders.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderCreateRequest(
        @NotNull
        @Positive
        Long customerId,

        @NotEmpty
        @Valid
        List<@NotNull @Valid OrderItemCreateRequest> items) {
}
