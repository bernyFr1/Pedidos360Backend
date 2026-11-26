package cl.duoc.pedidos360.orders.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica el evento de pedido después de confirmar la transacción.
 * Si Kafka falla, se registra el error pero no se revierte el pedido.
 */
@Component
public class PedidoKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public PedidoKafkaPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.topic.pedidos:pedidos-eventos}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(PedidoKafkaEvent event) {
        PedidoEventoKafka evento = event.evento();

        try {
            kafkaTemplate.send(topic, evento.key(), evento)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("No se pudo publicar el pedido {} en Kafka", evento.pedidoId(), error);
                            return;
                        }

                        if (result == null) {
                            log.info("Pedido publicado topic={} key={}", topic, evento.key());
                            return;
                        }

                        log.info("Pedido publicado topic={} key={} partition={} offset={}",
                                topic,
                                evento.key(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    });
        } catch (Exception exception) {
            log.error("Error publicando el pedido {} en Kafka", evento.pedidoId(), exception);
        }
    }
}
