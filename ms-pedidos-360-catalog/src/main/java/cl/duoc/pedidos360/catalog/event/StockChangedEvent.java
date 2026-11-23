package cl.duoc.pedidos360.catalog.event;

import cl.duoc.pedidos360.catalog.dto.StockChangeItem;

import java.time.Instant;
import java.util.List;

/**
 * Evento de dominio de cambio de stock. Es el contrato que luego se publicará
 * en Kafka (inventario-eventos); por ahora se registra en los logs.
 */
public record StockChangedEvent(
        Long orderId,
        String operation,
        List<StockChangeItem> items,
        Instant occurredAt
) {
}
