package cl.duoc.pedidos360.orders.kafka;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PedidoEventoFactory {

    public PedidoEventoKafka creado(Order order, String traceId) {
        return crear(order, PedidoEventoKafka.PEDIDO_CREADO, traceId);
    }

    public PedidoEventoKafka estadoCambiado(Order order, String traceId) {
        return crear(order, PedidoEventoKafka.PEDIDO_ESTADO_CAMBIADO, traceId);
    }

    public PedidoEventoKafka crear(Order order, String eventType, String traceId) {
        List<PedidoItemEventoKafka> items = order.getItems().stream()
                .map(this::item)
                .toList();

        return new PedidoEventoKafka(
                UUID.randomUUID().toString(),
                eventType,
                PedidoEventoKafka.SCHEMA_VERSION,
                order.getId(),
                order.getClientId(),
                order.getStatus(),
                items,
                order.getTotalAmount(),
                Instant.now(),
                traceId(traceId));
    }

    private PedidoItemEventoKafka item(OrderItem item) {
        return new PedidoItemEventoKafka(
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal());
    }

    private String traceId(String traceId) {
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId.trim();
    }
}
