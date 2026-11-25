package cl.duoc.pedidos360.orders.dto;

import java.math.BigDecimal;

public record StockChangeItem(
        Long productId,
        Integer quantity,
        Integer previousStock,
        Integer newStock,
        Integer delta,
        BigDecimal unitPrice
) {
}
