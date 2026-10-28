package cl.duoc.pedidos360.orders.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LabPedidoRequest(
        @NotNull Long pedidoId,
        @NotBlank @Email String clienteEmail,
        boolean falloEmail,
        boolean falloCocina,
        boolean falloFactura,
        @Pattern(regexp = "(?i)(CREADO|ACEPTADO|EN_PREPARACION|DESPACHADO|ENTREGADO|CANCELADO)",
                message = "Estado no válido")
        String estado,
        @Pattern(regexp = "(?i)(direct|topic)", message = "Exchange no válido")
        String exchange
) {
}
