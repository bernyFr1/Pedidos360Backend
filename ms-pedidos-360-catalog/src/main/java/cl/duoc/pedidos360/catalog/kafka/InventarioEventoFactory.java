package cl.duoc.pedidos360.catalog.kafka;

import cl.duoc.pedidos360.catalog.dto.StockChangeItem;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class InventarioEventoFactory {

    public List<InventarioEventoKafka> crear(StockChangedEvent event, String traceId) {
        String eventType = switch (event.operation()) {
            case "RESERVA" -> InventarioEventoKafka.STOCK_RESERVADO;
            case "LIBERACION" -> InventarioEventoKafka.STOCK_LIBERADO;
            default -> InventarioEventoKafka.STOCK_AJUSTADO;
        };

        String trace = traceId(traceId);

        return event.items().stream()
                .map(item -> evento(event, item, eventType, trace))
                .toList();
    }

    private InventarioEventoKafka evento(
            StockChangedEvent event,
            StockChangeItem item,
            String eventType,
            String traceId) {
        return new InventarioEventoKafka(
                UUID.randomUUID().toString(),
                eventType,
                InventarioEventoKafka.SCHEMA_VERSION,
                item.productId(),
                event.orderId(),
                item.previousStock(),
                item.delta(),
                item.newStock(),
                item.unitPrice(),
                event.operation(),
                event.occurredAt(),
                traceId);
    }

    private String traceId(String traceId) {
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId.trim();
    }
}
