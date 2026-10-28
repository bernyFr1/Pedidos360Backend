package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.config.RabbitProperties;
import cl.duoc.pedidos360.orders.dto.PedidoComando;
import cl.duoc.pedidos360.orders.dto.ResultadoPublicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PedidoEventPublisher publisher;

    @BeforeEach
    void setUp() {
        RabbitProperties properties = new RabbitProperties(
                List.of("email", "kitchen", "invoice"),
                new RabbitProperties.Exchange("cmd.direct", "cmd.topic", "cmd.dead.dlx"),
                Map.of("email", "q.cmd.email", "kitchen", "q.cmd.kitchen", "invoice", "q.cmd.invoice"),
                Map.of("email", "email.send", "kitchen", "kitchen.ticket", "invoice", "invoice.gen"),
                Map.of("email", "email.#", "kitchen", "kitchen.#", "invoice", "invoice.#")
        );
        publisher = new PedidoEventPublisher(rabbitTemplate, properties);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void directUsaRoutingKeysBase() {
        ResultadoPublicacion resultado = publisher.publicarComandos(new PedidoComando(
                1L, "cliente@example.com", "CREADO",
                false, false, false, "direct",
                List.of("email", "kitchen", "invoice")));

        assertEquals("cmd.direct", resultado.comandos().get(0).exchange());
        assertEquals("email.send", resultado.comandos().get(0).routingKey());
        assertEquals("kitchen.ticket", resultado.comandos().get(1).routingKey());
        assertEquals("invoice.gen", resultado.comandos().get(2).routingKey());

        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("kitchen.ticket"), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("invoice.gen"), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void topicUsaVariantesConSufijo() {
        ResultadoPublicacion resultado = publisher.publicarComandos(new PedidoComando(
                2L, "cliente@example.com", "ACEPTADO",
                false, false, false, "topic",
                List.of("email", "kitchen", "invoice")));

        assertEquals("cmd.topic", resultado.comandos().get(0).exchange());
        assertEquals("email.send.high", resultado.comandos().get(0).routingKey());
        assertEquals("kitchen.ticket.thermal", resultado.comandos().get(1).routingKey());
        assertEquals("invoice.gen.pdf", resultado.comandos().get(2).routingKey());

        verify(rabbitTemplate).convertAndSend(eq("cmd.topic"), eq("email.send.high"), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.topic"), eq("kitchen.ticket.thermal"), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.topic"), eq("invoice.gen.pdf"), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }
}
