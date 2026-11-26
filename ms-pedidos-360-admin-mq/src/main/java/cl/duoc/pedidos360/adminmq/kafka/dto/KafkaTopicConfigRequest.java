package cl.duoc.pedidos360.adminmq.kafka.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record KafkaTopicConfigRequest(
        @NotEmpty Map<String, String> configs
) {
}
