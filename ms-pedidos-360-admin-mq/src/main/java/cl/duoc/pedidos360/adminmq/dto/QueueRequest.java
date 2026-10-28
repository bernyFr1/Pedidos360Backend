package cl.duoc.pedidos360.adminmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record QueueRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Nombre de cola inválido")
        String name,
        Boolean durable
) {
}
