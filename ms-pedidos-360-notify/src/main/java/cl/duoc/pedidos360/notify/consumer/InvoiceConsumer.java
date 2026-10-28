package cl.duoc.pedidos360.notify.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InvoiceConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvoiceConsumer.class);

    private final ProcesadorMensajes procesador;

    public InvoiceConsumer(ProcesadorMensajes procesador) {
        this.procesador = procesador;
    }

    @RabbitListener(queues = "${app.rabbit.queues.invoice}")
    public void consumir(Message message, Channel channel) throws IOException {
        procesador.procesar(message, channel, "invoice", (evento, routingKey) -> {
            if (evento.pedidoId() == null) {
                throw new FalloPermanenteException("pedidoId vacío");
            }
            if (evento.estado() == null || evento.estado().isBlank()) {
                throw new FalloPermanenteException("estado vacío");
            }
            if (evento.falloFactura()) {
                throw new IllegalStateException("Fallo simulado de factura");
            }

            String accion = switch (evento.estado()) {
                case "DESPACHADO" -> "borrador generado";
                case "ENTREGADO" -> "final generada";
                case "CANCELADO" -> "anulada";
                default -> throw new FalloPermanenteException(
                        "Estado no facturable: " + evento.estado());
            };
            String formato = routingKey != null && routingKey.endsWith(".pdf")
                    ? "PDF"
                    : "ESTÁNDAR";

            log.info("Factura simulada {} [{}]: pedidoId={} estado={} routingKey={}",
                    accion, formato, evento.pedidoId(), evento.estado(), routingKey);
        });
    }
}
