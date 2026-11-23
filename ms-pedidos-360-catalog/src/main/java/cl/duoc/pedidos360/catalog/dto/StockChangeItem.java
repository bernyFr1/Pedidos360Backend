package cl.duoc.pedidos360.catalog.dto;

public record StockChangeItem(
        Long productId,
        Integer quantity,
        Integer previousStock,
        Integer newStock,
        Integer delta
) {
}
