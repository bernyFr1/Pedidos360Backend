package cl.duoc.pedidos360.notify.dlq;

import cl.duoc.pedidos360.notify.config.RabbitProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DlqInspector {

    private static final Logger log = LoggerFactory.getLogger(DlqInspector.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties properties;

    public DlqInspector(RabbitTemplate rabbitTemplate, RabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public Map<String, Integer> inspeccionarTodas() {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        for (String dominio : properties.dominios()) {
            String dlq = properties.dlq(dominio);
            resultado.put(dlq, inspeccionar(dlq));
        }
        log.info("Inspección DLQ completada: {}", resultado);
        return resultado;
    }

    public int inspeccionar(String dlq) {
        Integer inspeccionados = rabbitTemplate.execute(channel -> inspeccionarDlq(channel, dlq));
        return inspeccionados != null ? inspeccionados : 0;
    }

    private int inspeccionarDlq(Channel channel, String dlq) throws Exception {
        AMQP.Queue.DeclareOk declaracion = channel.queueDeclarePassive(dlq);
        int total = declaracion.getMessageCount();
        int inspeccionados = 0;

        for (int i = 0; i < total; i++) {
            GetResponse respuesta = channel.basicGet(dlq, false);
            if (respuesta == null) {
                break;
            }

            long deliveryTag = respuesta.getEnvelope().getDeliveryTag();
            try {
                registrarMensaje(dlq, respuesta);
                channel.basicPublish("", dlq, respuesta.getProps(), respuesta.getBody());
                channel.basicAck(deliveryTag, false);
                inspeccionados++;
            } catch (Exception exception) {
                channel.basicNack(deliveryTag, false, true);
                log.error("No se pudo reencolar mensaje DLQ queue={} deliveryTag={}",
                        dlq, deliveryTag, exception);
                throw exception;
            }
        }

        return inspeccionados;
    }

    private void registrarMensaje(String dlq, GetResponse respuesta) {
        Map<String, Object> headers = respuesta.getProps() != null
                ? respuesta.getProps().getHeaders()
                : null;
        Object xDeath = headers != null ? headers.get("x-death") : null;
        Object retryCount = headers != null ? headers.get("x-retry-count") : null;
        String messageId = respuesta.getProps() != null ? respuesta.getProps().getMessageId() : null;
        String payload = new String(respuesta.getBody(), StandardCharsets.UTF_8);

        log.info("DLQ inspección queue={} exchange={} routingKey={} messageId={} x-retry-count={} x-death={} payload={}",
                dlq,
                respuesta.getEnvelope().getExchange(),
                respuesta.getEnvelope().getRoutingKey(),
                messageId,
                retryCount,
                xDeath,
                payload);
    }
}
