package cl.duoc.pedidos360.orders.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoKafkaPublisherTest {

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void publicaConLaKeyDelPedido() {
        PedidoEventoKafka evento = evento();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new PedidoKafkaPublisher(kafkaTemplate, "pedidos-eventos")
                .publicar(new PedidoKafkaEvent(evento));

        verify(kafkaTemplate).send("pedidos-eventos", "123", evento);
    }

    @Test
    void noPropagaElErrorSiKafkaFalla() {
        CompletableFuture<SendResult<Object, Object>> falla = new CompletableFuture<>();
        falla.completeExceptionally(new IllegalStateException("Kafka no disponible"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(falla);

        PedidoKafkaPublisher publisher = new PedidoKafkaPublisher(kafkaTemplate, "pedidos-eventos");

        assertDoesNotThrow(() -> publisher.publicar(new PedidoKafkaEvent(evento())));
    }

    private PedidoEventoKafka evento() {
        return new PedidoEventoKafka(
                "evt-1",
                PedidoEventoKafka.PEDIDO_CREADO,
                1,
                123L,
                "cliente-1",
                "CREADO",
                List.of(new PedidoItemEventoKafka(81L, 2, new BigDecimal("10.00"), new BigDecimal("20.00"))),
                new BigDecimal("20.00"),
                Instant.parse("2026-11-25T12:00:00Z"),
                "trace-1");
    }
}
