package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.domain.PedidoPolicy;
import cl.duoc.pedidos360.orders.dto.PedidoComando;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoPersistidoListenerTest {

    @Mock
    private PedidoEventPublisher publisher;

    private PedidoPersistidoListener listener;

    @BeforeEach
    void setUp() {
        listener = new PedidoPersistidoListener(publisher, new PedidoPolicy());
    }

    @Test
    void alCrearPublicaSoloEmail() {
        listener.alPersistirPedido(new PedidoPersistidoEvent(1L, "cliente@example.com", "CREADO"));

        verify(publisher).publicarComandos(new PedidoComando(
                1L, "cliente@example.com", "CREADO",
                false, false, false, "direct", List.of("email")));
    }

    @Test
    void alAceptarPublicaEmailYCocina() {
        listener.alPersistirPedido(new PedidoPersistidoEvent(2L, "cliente@example.com", "ACEPTADO"));

        verify(publisher).publicarComandos(new PedidoComando(
                2L, "cliente@example.com", "ACEPTADO",
                false, false, false, "topic", List.of("email", "kitchen")));
    }

    @Test
    void alDespacharPublicaEmailYFactura() {
        listener.alPersistirPedido(new PedidoPersistidoEvent(3L, "cliente@example.com", "DESPACHADO"));

        verify(publisher).publicarComandos(new PedidoComando(
                3L, "cliente@example.com", "DESPACHADO",
                false, false, false, "topic", List.of("email", "invoice")));
    }
}
