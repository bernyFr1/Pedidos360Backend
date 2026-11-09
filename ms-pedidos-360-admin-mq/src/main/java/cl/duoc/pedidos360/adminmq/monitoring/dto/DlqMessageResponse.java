package cl.duoc.pedidos360.adminmq.monitoring.dto;

import java.util.List;
import java.util.Map;

/**
 * Mensaje inspeccionado desde una Dead Letter Queue.
 * "reference" identifica el mensaje para el requeue: usa message_id cuando existe
 * y, si no, una huella estable calculada sobre cola, payload y fecha de muerte.
 */
public record DlqMessageResponse(
        String queue,
        String reference,
        String messageId,
        String exchange,
        String routingKey,
        String reason,
        long deathCount,
        String failedAt,
        String payload,
        String payloadEncoding,
        boolean redelivered,
        Map<String, Object> headers,
        List<Map<String, Object>> xDeath
) {
}
