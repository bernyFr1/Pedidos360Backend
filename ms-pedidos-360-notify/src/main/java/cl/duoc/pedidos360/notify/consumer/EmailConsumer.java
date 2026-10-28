package cl.duoc.pedidos360.notify.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);

    private final ProcesadorMensajes procesador;

    public EmailConsumer(ProcesadorMensajes procesador) {
        this.procesador = procesador;
    }

    @RabbitListener(queues = "${app.rabbit.queues.email}")
    public void consumir(Message message, Channel channel) throws IOException {
        procesador.procesar(message, channel, "email", (evento, routingKey) -> {
            if (evento.clienteEmail() == null || evento.clienteEmail().isBlank()) {
                throw new FalloPermanenteException("clienteEmail vacío");
            }
            if (evento.estado() == null || evento.estado().isBlank()) {
                throw new FalloPermanenteException("estado vacío");
            }
            if (evento.falloEmail()) {
                throw new IllegalStateException("Fallo simulado de email");
            }

            String accion = switch (evento.estado()) {
                case "CREADO" -> "confirmación de pedido";
                case "ACEPTADO" -> "pedido aceptado";
                case "EN_PREPARACION" -> "pedido en preparación";
                case "DESPACHADO" -> "pedido despachado";
                case "ENTREGADO" -> "pedido entregado";
                case "CANCELADO" -> "pedido cancelado";
                default -> throw new FalloPermanenteException(
                        "Estado no soportado para email: " + evento.estado());
            };
            String prioridad = routingKey != null && routingKey.endsWith(".high")
                    ? "ALTA PRIORIDAD"
                    : "ESTÁNDAR";

            log.info("Email simulado de {} [{}]: pedidoId={} destino={} estado={} routingKey={}",
                    accion, prioridad, evento.pedidoId(), evento.clienteEmail(),
                    evento.estado(), routingKey);
        });
    }
}
