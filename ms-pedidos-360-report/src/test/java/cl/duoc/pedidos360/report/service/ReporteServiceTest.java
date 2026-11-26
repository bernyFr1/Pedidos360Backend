package cl.duoc.pedidos360.report.service;

import cl.duoc.pedidos360.report.kafka.InventarioEventoKafka;
import cl.duoc.pedidos360.report.kafka.PedidoEventoKafka;
import cl.duoc.pedidos360.report.kafka.PedidoItemEventoKafka;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReporteServiceTest {

    private final ReporteService service = new ReporteService();

    @Test
    void procesaPedidoEIncrementaContador() {
        service.procesarPedido(new PedidoEventoKafka(
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

        assertEquals(1, service.pedidosProcesados());
    }

    @Test
    void procesaInventarioYActualizaStock() {
        service.procesarInventario(new InventarioEventoKafka(
                "evt-2",
                InventarioEventoKafka.STOCK_RESERVADO,
                1,
                81L,
                123L,
                5,
                -2,
                3,
                new BigDecimal("10.00"),
                "RESERVA",
                Instant.now(),
                "trace-2"));

        assertEquals(1, service.inventarioProcesado());
        assertEquals(3, service.stockActual().get(81L));
    }
}
