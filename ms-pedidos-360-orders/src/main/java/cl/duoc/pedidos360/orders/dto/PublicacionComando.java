package cl.duoc.pedidos360.orders.dto;

public record PublicacionComando(
        String dominio,
        String exchange,
        String routingKey,
        String eventId
) {
}
