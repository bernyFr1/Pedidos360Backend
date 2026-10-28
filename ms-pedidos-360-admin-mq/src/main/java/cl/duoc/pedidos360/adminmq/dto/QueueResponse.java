package cl.duoc.pedidos360.adminmq.dto;

import org.springframework.amqp.core.QueueInformation;

public record QueueResponse(
        String name,
        long messages,
        int consumers
) {
    public static QueueResponse from(QueueInformation information) {
        return new QueueResponse(
                information.getName(),
                information.getMessageCount(),
                information.getConsumerCount()
        );
    }
}
