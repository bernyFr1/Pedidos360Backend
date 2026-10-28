package cl.duoc.pedidos360.orders.dto;

import java.util.List;

public record LabPedidoResponse(
        String estado,
        Long pedidoId,
        String correlationId,
        List<PublicacionComando> comandos
) {
}
