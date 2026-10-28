package cl.duoc.pedidos360.notify.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class KitchenConsumer {

    private static final Logger log = LoggerFactory.getLogger(KitchenConsumer.class);

    private final ProcesadorMensajes procesador;

    public KitchenConsumer(ProcesadorMensajes procesador) {
        this.procesador = procesador;
    }

    @RabbitListener(queues = "${app.rabbit.queues.kitchen}")
    public void consumir(Message message, Channel channel) throws IOException {
        procesador.procesar(message, channel, "kitchen", (evento, routingKey) -> {
            if (evento.pedidoId() == null) {
                throw new FalloPermanenteException("pedidoId vacío");
            }
            if (evento.estado() == null || evento.estado().isBlank()) {
                throw new FalloPermanenteException("estado vacío");
            }
            if (evento.falloCocina()) {
                throw new IllegalStateException("Fallo simulado de cocina");
            }

            String accion = switch (evento.estado()) {
                case "ACEPTADO" -> "generado";
                case "EN_PREPARACION" -> "actualizado";
                case "CANCELADO" -> "anulado";
                default -> throw new FalloPermanenteException(
                        "Estado no soportado para cocina: " + evento.estado());
            };
            String formato = routingKey != null && routingKey.endsWith(".thermal")
                    ? "TÉRMICO"
                    : "ESTÁNDAR";

            log.info("Ticket de cocina {} [{}]: pedidoId={} estado={} routingKey={}",
                    accion, formato, evento.pedidoId(), evento.estado(), routingKey);
        });
    }
}
