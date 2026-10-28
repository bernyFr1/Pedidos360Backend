package cl.duoc.pedidos360.adminmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Nombre de exchange inválido")
        String name,
        @NotBlank
        @Pattern(regexp = "(?i)(direct|topic|fanout|headers)", message = "Tipo de exchange inválido")
        String type,
        Boolean durable
) {
}
