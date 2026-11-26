package cl.duoc.pedidos360.catalog.kafka;

import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica los eventos de inventario después de confirmar la transacción.
 * Un cambio de stock con varios productos genera un evento por productId.
 */
@Component
public class InventarioEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventarioEventoPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final InventarioEventoFactory factory;
    private final String topic;

    public InventarioEventoPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            InventarioEventoFactory factory,
            @Value("${app.kafka.topic.inventario:inventario-eventos}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.factory = factory;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(StockChangedEvent event) {
        for (InventarioEventoKafka evento : factory.crear(event, null)) {
            try {
                kafkaTemplate.send(topic, evento.key(), evento)
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                log.error("No se pudo publicar stock productId={} en Kafka",
                                        evento.productId(), error);
                                return;
                            }

                            if (result == null) {
                                log.info("Stock publicado topic={} key={}", topic, evento.key());
                                return;
                            }

                            log.info("Stock publicado topic={} key={} partition={} offset={}",
                                    topic,
                                    evento.key(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        });
            } catch (Exception exception) {
                log.error("Error publicando stock productId={} en Kafka",
                        evento.productId(), exception);
            }
        }
    }
}
