package cl.duoc.pedidos360.orders.dto;

import java.util.List;

public record ResultadoPublicacion(
        String correlationId,
        List<PublicacionComando> comandos
) {
}
