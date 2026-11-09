package cl.duoc.pedidos360.adminmq.monitoring.dto;

public record RequeueResponse(
        boolean requeued,
        String queue,
        String reference,
        String targetExchange,
        String targetRoutingKey,
        String message
) {
}
