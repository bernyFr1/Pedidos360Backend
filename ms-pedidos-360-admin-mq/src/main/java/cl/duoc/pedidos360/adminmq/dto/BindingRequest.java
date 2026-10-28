package cl.duoc.pedidos360.adminmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BindingRequest(
        @NotBlank String queue,
        @NotBlank String exchange,
        @NotNull String routingKey
) {
}
