package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.common.dto.PedidoEventoDTO;
import cl.duoc.pedidos360.orders.config.RabbitProperties;
import cl.duoc.pedidos360.orders.dto.PedidoComando;
import cl.duoc.pedidos360.orders.dto.PublicacionComando;
import cl.duoc.pedidos360.orders.dto.ResultadoPublicacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisher.class);

    private static final Map<String, String> TOPIC_ROUTING_KEYS = Map.of(
            "email", "email.send.high",
            "kitchen", "kitchen.ticket.thermal",
            "invoice", "invoice.gen.pdf"
    );

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties properties;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public ResultadoPublicacion publicarComandos(PedidoComando comando) {
        String tipoExchange = comando.exchange() == null
                ? "direct"
                : comando.exchange().toLowerCase(Locale.ROOT);
        String exchange = switch (tipoExchange) {
            case "direct" -> properties.exchange().direct();
            case "topic" -> properties.exchange().topic();
            default -> throw new IllegalArgumentException("El exchange debe ser direct o topic");
        };

        List<String> dominios = comando.dominios();
        if (dominios == null || dominios.isEmpty()) {
            throw new IllegalArgumentException("El comando debe indicar al menos un dominio");
        }

        String correlationId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        List<PublicacionComando> publicaciones = new ArrayList<>();

        for (String dominio : dominios) {
            String routingKey = resolverRoutingKey(tipoExchange, dominio);
            PedidoEventoDTO evento = new PedidoEventoDTO(
                    tipoComando(dominio),
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    traceId,
                    correlationId,
                    comando.pedidoId(),
                    comando.estado(),
                    comando.clienteEmail(),
                    comando.falloEmail(),
                    comando.falloCocina(),
                    comando.falloFactura()
            );

            publicar(exchange, routingKey, dominio, evento);
            publicaciones.add(new PublicacionComando(dominio, exchange, routingKey, evento.eventId()));
        }

        return new ResultadoPublicacion(correlationId, publicaciones);
    }

    private String resolverRoutingKey(String tipoExchange, String dominio) {
        if ("direct".equals(tipoExchange)) {
            String routingKey = properties.routingKey(dominio);
            if (routingKey == null) {
                throw new IllegalArgumentException("Routing key no configurada para dominio: " + dominio);
            }
            return routingKey;
        }

        String routingKey = TOPIC_ROUTING_KEYS.get(dominio);
        if (routingKey == null) {
            throw new IllegalArgumentException("Variante topic no configurada para dominio: " + dominio);
        }
        return routingKey;
    }

    private void publicar(String exchange, String routingKey, String dominio, PedidoEventoDTO evento) {
        CorrelationData correlationData = new CorrelationData(evento.eventId());

        rabbitTemplate.convertAndSend(exchange, routingKey, evento, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(evento.eventId());
            message.getMessageProperties().setHeader("x-retry-count", 0);
            message.getMessageProperties().setHeader("x-dominio", dominio);
            return message;
        }, correlationData);

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(3, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ rechazó el comando " + evento.eventId() + ": " + confirm.getReason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "No se pudo confirmar la publicación de " + evento.eventId(), exception);
        }

        log.info("Comando publicado: dominio={} exchange={} routingKey={} pedidoId={} eventId={} correlationId={}",
                dominio, exchange, routingKey, evento.pedidoId(), evento.eventId(), evento.correlationId());
    }

    private String tipoComando(String dominio) {
        return switch (dominio) {
            case "email" -> "EMAIL_SEND";
            case "kitchen" -> "KITCHEN_TICKET";
            case "invoice" -> "INVOICE_GEN";
            default -> "UNKNOWN";
        };
    }
}
