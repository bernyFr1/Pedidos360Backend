package cl.duoc.pedidos360.adminmq.kafka.dto;

public record KafkaConsumerGroupResponse(
        String groupId,
        String state,
        boolean simpleConsumerGroup
) {
}
