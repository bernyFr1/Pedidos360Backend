package cl.duoc.pedidos360.audit.service;

import cl.duoc.pedidos360.audit.kafka.PedidoEventoKafka;
import cl.duoc.pedidos360.audit.kafka.PedidoItemEventoKafka;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditoriaServiceTest {

    @Test
    void registraEventoDePedido() {
        AuditoriaService service = new AuditoriaService();

        service.registrar(new PedidoEventoKafka(
                "evt-1",
                PedidoEventoKafka.PEDIDO_CREADO,
                1,
                123L,
                "cliente-1",
                "CREADO",
                List.of(new PedidoItemEventoKafka(81L, 2, new BigDecimal("10.00"), new BigDecimal("20.00"))),
                new BigDecimal("20.00"),
                Instant.now(),
                "trace-1"));

        assertEquals(1, service.totalEventos());
        assertTrue(service.ultimosEventos().get(0).contains("pedidoId=123"));
    }
}
