package cl.duoc.pedidos360.adminmq.monitoring.dto;

import jakarta.validation.constraints.NotBlank;

public record RequeueRequest(
        @NotBlank String queue,
        @NotBlank String reference
) {
}
