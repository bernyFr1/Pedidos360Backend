package cl.duoc.pedidos360.adminmq.kafka.dto;

public record KafkaNodeResponse(
        int id,
        String host,
        int port,
        String rack
) {
}
