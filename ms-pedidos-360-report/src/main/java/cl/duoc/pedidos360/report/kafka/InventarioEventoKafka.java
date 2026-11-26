package cl.duoc.pedidos360.report.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record InventarioEventoKafka(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @Min(1) int schemaVersion,
        @NotNull Long productId,
        Long orderId,
        @NotNull @PositiveOrZero Integer previousStock,
        @NotNull Integer delta,
        @NotNull @PositiveOrZero Integer newStock,
        @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
        @NotBlank String reason,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        @NotBlank String traceId
) {
    public static final String STOCK_RESERVADO = "STOCK_RESERVADO";
    public static final String STOCK_LIBERADO = "STOCK_LIBERADO";
    public static final String STOCK_AJUSTADO = "STOCK_AJUSTADO";

    public String key() {
        return String.valueOf(productId);
    }
}
