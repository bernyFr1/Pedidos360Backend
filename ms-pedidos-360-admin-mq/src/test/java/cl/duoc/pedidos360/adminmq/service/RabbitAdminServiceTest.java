package cl.duoc.pedidos360.adminmq.service;

import cl.duoc.pedidos360.adminmq.dto.BindingRequest;
import cl.duoc.pedidos360.adminmq.dto.ExchangeRequest;
import cl.duoc.pedidos360.adminmq.dto.QueueRequest;
import cl.duoc.pedidos360.adminmq.dto.QueueResponse;
import cl.duoc.pedidos360.adminmq.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private RabbitAdmin rabbitAdmin;

    @InjectMocks
    private RabbitAdminService service;

    @Test
    void creaColaYDevuelveSusDatos() {
        when(rabbitAdmin.declareQueue(any(Queue.class))).thenReturn("q.test");
        when(rabbitAdmin.getQueueInfo("q.test"))
                .thenReturn(new QueueInformation("q.test", 5, 1));

        QueueResponse response = service.crearCola(new QueueRequest("q.test", true));

        assertEquals("q.test", response.name());
        assertEquals(5, response.messages());
        assertEquals(1, response.consumers());
        verify(rabbitAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void obtenerColaInexistenteLanza404() {
        when(rabbitAdmin.getQueueInfo("missing")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.obtenerCola("missing"));
    }

    @Test
    void eliminarColaInexistenteLanza404() {
        when(rabbitAdmin.deleteQueue("missing")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.eliminarCola("missing"));
    }

    @Test
    void crearExchangeTopicUsaTopicExchange() {
        service.crearExchange(new ExchangeRequest("ex.topic", "topic", true));

        verify(rabbitAdmin).declareExchange(any(TopicExchange.class));
    }

    @Test
    void crearBindingUsaExchangeRoutingKeyYCola() {
        when(rabbitAdmin.getQueueInfo("q.test"))
                .thenReturn(new QueueInformation("q.test", 0, 0));

        service.crearBinding(new BindingRequest("q.test", "ex.topic", "email.send.high"));

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(rabbitAdmin).declareBinding(captor.capture());
        assertEquals("q.test", captor.getValue().getDestination());
        assertEquals("ex.topic", captor.getValue().getExchange());
        assertEquals("email.send.high", captor.getValue().getRoutingKey());
    }
}
