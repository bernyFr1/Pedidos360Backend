package cl.duoc.pedidos360.orders.dto;

import java.util.List;

public record StockReservationResponse(
        Long orderId,
        String operation,
        List<StockChangeItem> items
) {
}
