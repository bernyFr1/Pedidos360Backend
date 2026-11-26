package cl.duoc.pedidos360.catalog.kafka;

import cl.duoc.pedidos360.catalog.dto.StockChangeItem;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioEventoPublisherTest {

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void publicaUnEventoPorProductoConSuKey() {
        StockChangedEvent cambio = new StockChangedEvent(
                10L,
                "RESERVA",
                List.of(
                        new StockChangeItem(81L, 2, 5, 3, -2, new BigDecimal("10.00")),
                        new StockChangeItem(82L, 1, 4, 3, -1, new BigDecimal("12.00"))),
                Instant.parse("2026-11-25T12:00:00Z"));

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new InventarioEventoPublisher(kafkaTemplate, new InventarioEventoFactory(), "inventario-eventos")
                .publicar(cambio);

        verify(kafkaTemplate).send(eq("inventario-eventos"), eq("81"), any(InventarioEventoKafka.class));
        verify(kafkaTemplate).send(eq("inventario-eventos"), eq("82"), any(InventarioEventoKafka.class));
    }
}
