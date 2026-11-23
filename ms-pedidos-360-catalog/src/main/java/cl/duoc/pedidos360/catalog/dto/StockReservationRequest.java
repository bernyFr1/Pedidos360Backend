package cl.duoc.pedidos360.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StockReservationRequest(
        @NotNull Long orderId,
        @NotEmpty List<@Valid StockItemRequest> items
) {
}
