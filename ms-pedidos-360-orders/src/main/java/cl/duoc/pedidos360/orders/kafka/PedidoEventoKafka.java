package cl.duoc.pedidos360.orders.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contrato del evento de pedido para el tópico pedidos-eventos.
 * La key del mensaje es el pedidoId para conservar el orden por pedido.
 */
public record PedidoEventoKafka(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @Min(1) int schemaVersion,
        @NotNull Long pedidoId,
        @NotBlank String clientId,
        @NotBlank String status,
        @NotEmpty List<@Valid PedidoItemEventoKafka> items,
        @NotNull @DecimalMin("0.0") BigDecimal totalAmount,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        @NotBlank String traceId
) {

    public static final int SCHEMA_VERSION = 1;
    public static final String PEDIDO_CREADO = "PEDIDO_CREADO";
    public static final String PEDIDO_ESTADO_CAMBIADO = "PEDIDO_ESTADO_CAMBIADO";

    public String key() {
        return String.valueOf(pedidoId);
    }
}
