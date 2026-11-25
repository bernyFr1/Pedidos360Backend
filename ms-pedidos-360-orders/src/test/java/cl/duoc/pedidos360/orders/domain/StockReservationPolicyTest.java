package cl.duoc.pedidos360.orders.domain;

import cl.duoc.pedidos360.orders.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockReservationPolicyTest {

    private static final LocalDateTime CORTE = LocalDateTime.parse("2026-11-24T00:00:00");

    @Test
    void liberaStockDePedidosCreadosDespuesDelCorte() {
        StockReservationPolicy policy = new StockReservationPolicy(CORTE.toString());
        Order pedido = pedidoConFecha(CORTE.plusMinutes(1));

        assertTrue(policy.debeLiberar(pedido));
    }

    @Test
    void noLiberaStockDePedidosAnterioresAlCorte() {
        StockReservationPolicy policy = new StockReservationPolicy(CORTE.toString());
        Order pedido = pedidoConFecha(CORTE.minusMinutes(1));

        assertFalse(policy.debeLiberar(pedido));
    }

    @Test
    void sinFechaAsumePedidoNuevo() {
        StockReservationPolicy policy = new StockReservationPolicy(CORTE.toString());

        assertTrue(policy.debeLiberar(new Order()));
    }

    @Test
    void sinConfiguracionSiempreLibera() {
        StockReservationPolicy policy = new StockReservationPolicy("");

        assertTrue(policy.debeLiberar(pedidoConFecha(CORTE.minusDays(30))));
    }

    private Order pedidoConFecha(LocalDateTime fecha) {
        Order pedido = new Order();
        ReflectionTestUtils.setField(pedido, "createdAt", fecha);
        return pedido;
    }
}
