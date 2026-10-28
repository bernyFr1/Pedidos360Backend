package cl.duoc.pedidos360.orders.messaging;

public record PedidoPersistidoEvent(
        Long pedidoId,
        String clienteEmail,
        String estado
) {
}
