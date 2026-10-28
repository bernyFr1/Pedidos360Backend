package cl.duoc.pedidos360.notify.consumer;

import cl.duoc.pedidos360.common.dto.PedidoEventoDTO;
import cl.duoc.pedidos360.notify.config.RabbitProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

@Component
public class ProcesadorMensajes {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorMensajes.class);

    private final RabbitTemplate rabbitTemplate;
    private final JacksonJsonMessageConverter jsonMessageConverter;
    private final RabbitProperties rabbitProperties;
    private final int maxIntentos;

    public ProcesadorMensajes(RabbitTemplate rabbitTemplate,
                              JacksonJsonMessageConverter jsonMessageConverter,
                              RabbitProperties rabbitProperties,
                              @Value("${app.rabbit.max-intentos:3}") int maxIntentos) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMessageConverter = jsonMessageConverter;
        this.rabbitProperties = rabbitProperties;
        this.maxIntentos = maxIntentos;
    }

    public void procesar(Message message, Channel channel, String dominio,
                         BiConsumer<PedidoEventoDTO, String> logica) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        long deliveryTag = properties.getDeliveryTag();
        int intento = obtenerIntento(properties);
        String exchange = properties.getReceivedExchange();
        String routingKey = properties.getReceivedRoutingKey();

        PedidoEventoDTO evento;
        try {
            evento = (PedidoEventoDTO) jsonMessageConverter.fromMessage(message);
            if (evento == null) {
                throw new MessageConversionException("Mensaje sin contenido");
            }
        } catch (Exception exception) {
            registrarDeadLetter(message, dominio, intento, null, "conversión: " + exception.getMessage());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        try {
            logica.accept(evento, routingKey);
            channel.basicAck(deliveryTag, false);
            log.info("ACK dominio={} eventId={} pedidoId={} correlationId={} intento={} routingKey={}",
                    dominio, evento.eventId(), evento.pedidoId(), evento.correlationId(),
                    intento, routingKey);
        } catch (FalloPermanenteException exception) {
            registrarDeadLetter(message, dominio, intento, evento, "permanente: " + exception.getMessage());
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception exception) {
            if (intento >= maxIntentos) {
                registrarDeadLetter(message, dominio, intento, evento,
                        "intentos agotados: " + exception.getMessage());
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            try {
                republicar(message, exchange, routingKey, intento + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Reintento publicado dominio={} eventId={} pedidoId={} intento={} routingKey={}",
                        dominio, evento.eventId(), evento.pedidoId(), intento + 1, routingKey);
            } catch (Exception publishException) {
                registrarDeadLetter(message, dominio, intento, evento,
                        "fallo al republicar: " + publishException.getMessage());
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private void registrarDeadLetter(Message message, String dominio, int intento,
                                     PedidoEventoDTO evento, String motivo) {
        MessageProperties messageProperties = message.getMessageProperties();
        String eventId = evento != null ? evento.eventId() : messageProperties.getMessageId();
        Long pedidoId = evento != null ? evento.pedidoId() : null;
        String correlationId = evento != null ? evento.correlationId() : null;
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        log.error("NACK -> DLQ queue={} exchange={} eventId={} pedidoId={} correlationId={} intento={} routingKey={} motivo={} payload={}",
                rabbitProperties.dlq(dominio),
                rabbitProperties.exchange().dlx(),
                eventId != null ? eventId : "desconocido",
                pedidoId != null ? pedidoId : "desconocido",
                correlationId != null ? correlationId : "desconocido",
                intento,
                messageProperties.getReceivedRoutingKey(),
                motivo,
                payload);
    }

    private void republicar(Message original, String exchange, String routingKey, int siguienteIntento)
            throws ExecutionException, InterruptedException, TimeoutException {
        MessageProperties retryProperties = new MessageProperties();
        retryProperties.getHeaders().putAll(original.getMessageProperties().getHeaders());
        retryProperties.setContentType(original.getMessageProperties().getContentType());
        retryProperties.setContentEncoding(original.getMessageProperties().getContentEncoding());
        retryProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        retryProperties.setMessageId(original.getMessageProperties().getMessageId());
        retryProperties.setHeader("x-retry-count", siguienteIntento);

        Message retryMessage = new Message(original.getBody(), retryProperties);
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.send(exchange, routingKey, retryMessage, correlationData);

        CorrelationData.Confirm confirm = correlationData.getFuture().get(3, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ rechazó el reintento: " + confirm.getReason());
        }
    }

    private int obtenerIntento(MessageProperties properties) {
        Object value = properties.getHeaders().get("x-retry-count");
        return value instanceof Number number ? number.intValue() : 0;
    }
}
