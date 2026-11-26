package cl.duoc.pedidos360.adminmq.kafka.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record KafkaTopicRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Nombre de tópico inválido")
        String name,
        @Min(1) int partitions,
        @Min(1) int replicationFactor,
        Map<String, String> configs
) {
}
