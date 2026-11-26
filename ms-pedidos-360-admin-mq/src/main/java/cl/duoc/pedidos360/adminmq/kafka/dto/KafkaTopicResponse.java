package cl.duoc.pedidos360.adminmq.kafka.dto;

import java.util.List;
import java.util.Map;

public record KafkaTopicResponse(
        String name,
        boolean internal,
        int partitions,
        int replicationFactor,
        Map<String, String> configs,
        List<KafkaPartitionResponse> partitionDetails
) {
}
