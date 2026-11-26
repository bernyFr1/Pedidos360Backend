package cl.duoc.pedidos360.adminmq.kafka.dto;

public record KafkaConsumerGroupOffsetResponse(
        String topic,
        int partition,
        long currentOffset,
        long logEndOffset,
        long lag
) {
}
