package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.client.CatalogStockClient;
import cl.duoc.pedidos360.orders.domain.PedidoPolicy;
import cl.duoc.pedidos360.orders.dto.StockChangeItem;
import cl.duoc.pedidos360.orders.dto.StockReservationResponse;
import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import cl.duoc.pedidos360.orders.messaging.PedidoPersistidoEvent;
import cl.duoc.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CatalogStockClient catalogStockClient;

    @Spy
    private PedidoPolicy pedidoPolicy = new PedidoPolicy();

    @InjectMocks
    private OrderService orderService;

    @Test
    void devuelvePedidosCuandoExisten() {
        Order order = new Order();
        order.setId(1L);
        when(orderRepository.findAll()).thenReturn(List.of(order));

        assertEquals(1, orderService.obtenerTodos().size());
        assertEquals(1L, orderService.obtenerTodos().get(0).getId());
    }

    @Test
    void devuelveListaVaciaCuandoNoExistenPedidos() {
        when(orderRepository.findAll()).thenReturn(List.of());

        assertEquals(List.of(), orderService.obtenerTodos());
    }

    @Test
    void devuelveSoloPedidosDelClienteSolicitado() {
        Order order = new Order();
        order.setId(1L);
        order.setClientId("cliente-1");
        when(orderRepository.findByClientId("cliente-1")).thenReturn(List.of(order));

        List<Order> pedidos = orderService.obtenerPorCliente("cliente-1");

        assertEquals(1, pedidos.size());
        assertEquals("cliente-1", pedidos.get(0).getClientId());
    }

    @Test
    void propagaErrorDeBaseDeDatosParaQueElHandlerLoRegistre() {
        when(orderRepository.findAll()).thenThrow(new DataAccessResourceFailureException("Oracle no disponible"));

        assertThrows(DataAccessResourceFailureException.class, orderService::obtenerTodos);
    }

    @Test
    void publicaEventoAlCambiarEstado() {
        Order pedido = new Order();
        pedido.setId(10L);
        pedido.setClientId("cliente-1");
        pedido.setStatus("CREADO");
        when(orderRepository.findById(10L)).thenReturn(Optional.of(pedido));
        when(orderRepository.save(pedido)).thenReturn(pedido);

        Order actualizado = orderService.cambiarEstado(10L, "ACEPTADO", "token");

        assertEquals("ACEPTADO", actualizado.getStatus());
        verify(eventPublisher).publishEvent(new PedidoPersistidoEvent(10L, "cliente-1", "ACEPTADO"));
        verifyNoInteractions(catalogStockClient);
    }

    @Test
    void rechazaTransicionInvalida() {
        Order pedido = new Order();
        pedido.setId(11L);
        pedido.setStatus("CREADO");
        when(orderRepository.findById(11L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> orderService.cambiarEstado(11L, "ENTREGADO", "token"));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(catalogStockClient);
    }

    @Test
    void noPublicaSiElEstadoNoCambia() {
        Order pedido = new Order();
        pedido.setId(12L);
        pedido.setStatus("CREADO");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(pedido));

        Order resultado = orderService.cambiarEstado(12L, "CREADO", "token");

        assertEquals("CREADO", resultado.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(catalogStockClient);
    }

    @Test
    void creaPedidoReservandoStockAntesDePublicarComandos() {
        Order pedido = new Order();
        pedido.setItems(List.of(item(1L, 2, "10.00")));

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order guardado = invocation.getArgument(0);
            guardado.setId(99L);
            return guardado;
        });
        when(catalogStockClient.reservar(eq(99L), anyList(), eq("token")))
                .thenReturn(new StockReservationResponse(
                        99L,
                        "RESERVA",
                        List.of(new StockChangeItem(1L, 2, 5, 3, -2))));

        Order guardado = orderService.crearPedido(pedido, "cliente-1", "token");

        assertEquals(99L, guardado.getId());
        assertEquals("CREADO", guardado.getStatus());
        assertEquals(new BigDecimal("20.00"), guardado.getTotalAmount());
        verify(catalogStockClient).reservar(eq(99L), anyList(), eq("token"));
        verify(eventPublisher).publishEvent(any(PedidoPersistidoEvent.class));
    }

    @Test
    void noPublicaComandosSiLaReservaDeStockFalla() {
        Order pedido = new Order();
        pedido.setItems(List.of(item(1L, 2, "10.00")));

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order guardado = invocation.getArgument(0);
            guardado.setId(100L);
            return guardado;
        });
        when(catalogStockClient.reservar(eq(100L), anyList(), eq("token")))
                .thenThrow(new IllegalStateException("Stock insuficiente para Producto"));

        assertThrows(IllegalStateException.class,
                () -> orderService.crearPedido(pedido, "cliente-1", "token"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelarPedidoLiberaStock() {
        Order pedido = new Order();
        pedido.setId(20L);
        pedido.setClientId("cliente-1");
        pedido.setStatus("ACEPTADO");
        pedido.setItems(List.of(item(1L, 2, "10.00")));

        when(orderRepository.findById(20L)).thenReturn(Optional.of(pedido));
        when(orderRepository.save(pedido)).thenReturn(pedido);
        when(catalogStockClient.liberar(eq(20L), anyList(), eq("token")))
                .thenReturn(new StockReservationResponse(
                        20L,
                        "LIBERACION",
                        List.of(new StockChangeItem(1L, 2, 3, 5, 2))));

        Order cancelado = orderService.cambiarEstado(20L, "CANCELADO", "token");

        assertEquals("CANCELADO", cancelado.getStatus());
        verify(catalogStockClient).liberar(eq(20L), anyList(), eq("token"));
        verify(eventPublisher).publishEvent(new PedidoPersistidoEvent(20L, "cliente-1", "CANCELADO"));
    }

    private OrderItem item(Long productId, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }
}
