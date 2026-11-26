package cl.duoc.pedidos360.catalog.kafka;

import cl.duoc.pedidos360.catalog.dto.StockChangeItem;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventarioEventoKafkaTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void factoryConvierteReservaEnEventoPorProducto() {
        StockChangedEvent cambio = new StockChangedEvent(
                10L,
                "RESERVA",
                List.of(new StockChangeItem(81L, 2, 5, 3, -2, new BigDecimal("10.00"))),
                Instant.parse("2026-11-25T12:00:00Z"));

        List<InventarioEventoKafka> eventos = new InventarioEventoFactory().crear(cambio, "trace-1");

        assertEquals(1, eventos.size());
        InventarioEventoKafka evento = eventos.get(0);
        assertEquals(InventarioEventoKafka.STOCK_RESERVADO, evento.eventType());
        assertEquals("81", evento.key());
        assertEquals(5, evento.previousStock());
        assertEquals(-2, evento.delta());
        assertEquals(3, evento.newStock());
        assertEquals("trace-1", evento.traceId());
    }

    @Test
    void serializaYDeserializaElContrato() {
        InventarioEventoKafka evento = new InventarioEventoKafka(
                "evt-1",
                InventarioEventoKafka.STOCK_RESERVADO,
                1,
                81L,
                10L,
                5,
                -2,
                3,
                new BigDecimal("10.00"),
                "RESERVA",
                Instant.parse("2026-11-25T12:00:00Z"),
                "trace-1");

        String json = mapper.writeValueAsString(evento);
        assertTrue(json.contains("\"occurredAt\":\""), json);
        InventarioEventoKafka recuperado = mapper.readValue(json, InventarioEventoKafka.class);

        assertEquals(evento.eventId(), recuperado.eventId());
        assertEquals(evento.productId(), recuperado.productId());
        assertEquals(evento.previousStock(), recuperado.previousStock());
        assertEquals(evento.newStock(), recuperado.newStock());
        assertEquals(evento.occurredAt(), recuperado.occurredAt());
    }

    @Test
    void validaStockNegativo() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        InventarioEventoKafka evento = new InventarioEventoKafka(
                "evt-1",
                InventarioEventoKafka.STOCK_RESERVADO,
                1,
                81L,
                10L,
                -1,
                -2,
                -3,
                new BigDecimal("10.00"),
                "RESERVA",
                Instant.now(),
                "trace-1");

        Set<ConstraintViolation<InventarioEventoKafka>> violations = validator.validate(evento);

        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("previousStock")));
    }
}
