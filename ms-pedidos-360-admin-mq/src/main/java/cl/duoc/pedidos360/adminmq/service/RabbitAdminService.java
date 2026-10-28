package cl.duoc.pedidos360.adminmq.service;

import cl.duoc.pedidos360.adminmq.dto.BindingRequest;
import cl.duoc.pedidos360.adminmq.dto.ExchangeRequest;
import cl.duoc.pedidos360.adminmq.dto.QueueRequest;
import cl.duoc.pedidos360.adminmq.dto.QueueResponse;
import cl.duoc.pedidos360.adminmq.exception.ResourceNotFoundException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class RabbitAdminService {

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    public QueueResponse crearCola(QueueRequest request) {
        boolean durable = request.durable() == null || request.durable();
        Queue queue = new Queue(request.name(), durable, false, false);
        String declared = rabbitAdmin.declareQueue(queue);
        if (declared == null) {
            throw new AmqpException("No se pudo declarar la cola " + request.name());
        }
        return obtenerCola(request.name());
    }

    public QueueResponse obtenerCola(String name) {
        QueueInformation information = rabbitAdmin.getQueueInfo(name);
        if (information == null) {
            throw new ResourceNotFoundException("Cola no encontrada: " + name);
        }
        return QueueResponse.from(information);
    }

    public void eliminarCola(String name) {
        if (!rabbitAdmin.deleteQueue(name)) {
            throw new ResourceNotFoundException("Cola no encontrada: " + name);
        }
    }

    public void crearExchange(ExchangeRequest request) {
        boolean durable = request.durable() == null || request.durable();
        String type = request.type().toLowerCase(Locale.ROOT);
        Exchange exchange = switch (type) {
            case "direct" -> new DirectExchange(request.name(), durable, false);
            case "topic" -> new TopicExchange(request.name(), durable, false);
            case "fanout" -> new FanoutExchange(request.name(), durable, false);
            case "headers" -> new HeadersExchange(request.name(), durable, false);
            default -> throw new IllegalArgumentException("Tipo de exchange inválido: " + request.type());
        };
        rabbitAdmin.declareExchange(exchange);
    }

    public void eliminarExchange(String name) {
        if (!rabbitAdmin.deleteExchange(name)) {
            throw new ResourceNotFoundException("Exchange no encontrado: " + name);
        }
    }

    public void crearBinding(BindingRequest request) {
        if (rabbitAdmin.getQueueInfo(request.queue()) == null) {
            throw new ResourceNotFoundException("Cola no encontrada: " + request.queue());
        }
        rabbitAdmin.declareBinding(binding(request));
    }

    public void eliminarBinding(BindingRequest request) {
        rabbitAdmin.removeBinding(binding(request));
    }

    private Binding binding(BindingRequest request) {
        return new Binding(
                request.queue(),
                Binding.DestinationType.QUEUE,
                request.exchange(),
                request.routingKey(),
                null
        );
    }
}
