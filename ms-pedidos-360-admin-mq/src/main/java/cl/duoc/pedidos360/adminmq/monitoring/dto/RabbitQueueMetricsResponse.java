package cl.duoc.pedidos360.adminmq.monitoring.dto;

/**
 * Métricas de una cola clave del sistema.
 */
public record RabbitQueueMetricsResponse(
        String name,
        long ready,
        long unacked,
        int consumers,
        double incomingRate,
        double deliverRate
) {
    public static RabbitQueueMetricsResponse empty(String name) {
        return new RabbitQueueMetricsResponse(name, 0, 0, 0, 0, 0);
    }
}
