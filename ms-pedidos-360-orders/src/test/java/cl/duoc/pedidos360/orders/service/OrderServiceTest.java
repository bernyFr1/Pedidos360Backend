package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.domain.PedidoPolicy;
import cl.duoc.pedidos360.orders.entity.Order;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

        Order actualizado = orderService.cambiarEstado(10L, "ACEPTADO");

        assertEquals("ACEPTADO", actualizado.getStatus());
        verify(eventPublisher).publishEvent(new PedidoPersistidoEvent(10L, "cliente-1", "ACEPTADO"));
    }

    @Test
    void rechazaTransicionInvalida() {
        Order pedido = new Order();
        pedido.setId(11L);
        pedido.setStatus("CREADO");
        when(orderRepository.findById(11L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> orderService.cambiarEstado(11L, "ENTREGADO"));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void noPublicaSiElEstadoNoCambia() {
        Order pedido = new Order();
        pedido.setId(12L);
        pedido.setStatus("CREADO");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(pedido));

        Order resultado = orderService.cambiarEstado(12L, "CREADO");

        assertEquals("CREADO", resultado.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(eventPublisher);
    }
}
