package cl.duoc.pedidos360.adminmq.kafka.dto;

import java.util.List;

public record KafkaConsumerGroupDetailResponse(
        String groupId,
        String state,
        int members,
        List<KafkaConsumerGroupOffsetResponse> offsets
) {
}
