package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

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
}