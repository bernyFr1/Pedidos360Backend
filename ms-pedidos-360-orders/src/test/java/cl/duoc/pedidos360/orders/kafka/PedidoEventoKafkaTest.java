package cl.duoc.pedidos360.orders.kafka;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedidoEventoKafkaTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void factoryConstruyeContratoConKeyPorPedido() {
        Order pedido = pedido();

        PedidoEventoKafka evento = new PedidoEventoFactory().creado(pedido, "trace-1");

        assertEquals("PEDIDO_CREADO", evento.eventType());
        assertEquals(1, evento.schemaVersion());
        assertEquals("123", evento.key());
        assertEquals("cliente-1", evento.clientId());
        assertEquals(new BigDecimal("20.00"), evento.totalAmount());
        assertEquals(1, evento.items().size());
        assertEquals("trace-1", evento.traceId());
    }

    @Test
    void serializaYDeserializaElContrato() {
        PedidoEventoKafka evento = new PedidoEventoFactory().creado(pedido(), "trace-1");

        String json = mapper.writeValueAsString(evento);
        assertTrue(json.contains("\"occurredAt\":\""), json);
        PedidoEventoKafka recuperado = mapper.readValue(json, PedidoEventoKafka.class);

        assertEquals(evento.eventId(), recuperado.eventId());
        assertEquals(evento.eventType(), recuperado.eventType());
        assertEquals(evento.pedidoId(), recuperado.pedidoId());
        assertEquals(evento.items().get(0).productId(), recuperado.items().get(0).productId());
        assertEquals(evento.occurredAt(), recuperado.occurredAt());
    }

    @Test
    void serializadorDeSpringProduceFechaEnIso() {
        JsonSerializer serializer = new JsonSerializer();
        PedidoEventoKafka evento = new PedidoEventoFactory().creado(pedido(), "trace-1");

        String json = new String(serializer.serialize("pedidos-eventos", evento), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"occurredAt\":\""), json);
    }

    @Test
    void validaQueLasCantidadesSeanPositivas() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        PedidoEventoKafka evento = new PedidoEventoKafka(
                "evt-1",
                PedidoEventoKafka.PEDIDO_CREADO,
                1,
                123L,
                "cliente-1",
                "CREADO",
                List.of(new PedidoItemEventoKafka(1L, 0, BigDecimal.TEN, BigDecimal.ZERO)),
                BigDecimal.ZERO,
                Instant.now(),
                "trace-1");

        Set<ConstraintViolation<PedidoEventoKafka>> violations = validator.validate(evento);

        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("quantity")));
    }

    private Order pedido() {
        Order order = new Order();
        order.setId(123L);
        order.setClientId("cliente-1");
        order.setStatus("CREADO");
        order.setTotalAmount(new BigDecimal("20.00"));

        OrderItem item = new OrderItem();
        item.setProductId(81L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setSubtotal(new BigDecimal("20.00"));
        order.setItems(List.of(item));

        return order;
    }
}
