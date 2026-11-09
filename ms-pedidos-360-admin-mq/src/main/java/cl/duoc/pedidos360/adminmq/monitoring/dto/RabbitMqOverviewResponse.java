package cl.duoc.pedidos360.adminmq.monitoring.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resumen de salud y throughput del broker.
 */
public record RabbitMqOverviewResponse(
        String status,
        String version,
        String node,
        long queues,
        long consumers,
        long messagesReady,
        long messagesUnacked,
        double publishRate,
        double ackRate,
        double deliverRate,
        long memoryUsed,
        long memoryLimit,
        long diskFree,
        long diskFreeLimit,
        long dlqMessages,
        List<String> alarms,
        Instant timestamp
) {
    public static RabbitMqOverviewResponse down(String reason) {
        return new RabbitMqOverviewResponse(
                "DOWN", "—", "—",
                0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                0,
                List.of(reason),
                Instant.now());
    }
}
