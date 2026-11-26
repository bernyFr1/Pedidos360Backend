package cl.duoc.pedidos360.adminmq.kafka.dto;

import java.util.List;

public record KafkaClusterResponse(
        String clusterId,
        int controllerId,
        List<KafkaNodeResponse> nodes
) {
}
