package cl.duoc.pedidos360.orders.dto;

import java.util.List;

public record PedidoComando(
        Long pedidoId,
        String clienteEmail,
        String estado,
        boolean falloEmail,
        boolean falloCocina,
        boolean falloFactura,
        String exchange,
        List<String> dominios
) {
}
