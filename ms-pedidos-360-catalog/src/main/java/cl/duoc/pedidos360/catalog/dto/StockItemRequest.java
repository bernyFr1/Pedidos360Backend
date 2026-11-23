package cl.duoc.pedidos360.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {
}
