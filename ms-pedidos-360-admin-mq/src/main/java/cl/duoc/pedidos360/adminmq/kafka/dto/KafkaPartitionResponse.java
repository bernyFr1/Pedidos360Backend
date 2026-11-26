package cl.duoc.pedidos360.adminmq.kafka.dto;

import java.util.List;

public record KafkaPartitionResponse(
        int partition,
        int leader,
        List<Integer> replicas,
        List<Integer> isr
) {
}
