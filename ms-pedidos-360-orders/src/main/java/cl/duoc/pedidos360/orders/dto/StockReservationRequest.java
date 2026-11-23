package cl.duoc.pedidos360.orders.dto;

import java.util.List;

public record StockReservationRequest(
        Long orderId,
        List<StockItemRequest> items
) {
}
