package cl.duoc.pedidos360.orders.dto;

public record StockItemRequest(
        Long productId,
        Integer quantity
) {
}
