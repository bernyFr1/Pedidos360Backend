package cl.duoc.pedidos360.common.dto;

import java.time.LocalDateTime;

public record PedidoEventoDTO(
        String type,
        String eventId,
        LocalDateTime timestamp,
        String traceId,
        String correlationId,
        Long pedidoId,
        String estado,
        String clienteEmail,
        boolean falloEmail,
        boolean falloCocina,
        boolean falloFactura
) {
}
