package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.domain.EstadoPedido;
import cl.duoc.pedidos360.orders.domain.PedidoPolicy;
import cl.duoc.pedidos360.orders.dto.StockReservationResponse;
import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import cl.duoc.pedidos360.orders.messaging.PedidoPersistidoEvent;
import cl.duoc.pedidos360.orders.repository.OrderRepository;
import cl.duoc.pedidos360.orders.client.CatalogStockClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PedidoPolicy pedidoPolicy;
    private final CatalogStockClient catalogStockClient;

    public OrderService(OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher,
                        PedidoPolicy pedidoPolicy,
                        CatalogStockClient catalogStockClient) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.pedidoPolicy = pedidoPolicy;
        this.catalogStockClient = catalogStockClient;
    }

    @Transactional(readOnly = true)
    public List<Order> obtenerTodos() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Order> obtenerPedidos(String cuenta, boolean puedeVerTodos) {
        if (puedeVerTodos) {
            return obtenerTodos();
        }
        return obtenerPorCliente(cuenta);
    }

    @Transactional(readOnly = true)
    public List<Order> obtenerPorCliente(String clientId) {
        return orderRepository.findByClientId(clientId);
    }

    @Transactional(readOnly = true)
    public Order obtenerPorId(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public Order obtenerPorId(Long id, String cuenta, boolean puedeVerTodos) {
        Order pedido = obtenerPorId(id);
        if (!puedeVerTodos && !cuenta.equals(pedido.getClientId())) {
            throw new RuntimeException("Pedido no encontrado con ID: " + id);
        }
        return pedido;
    }

    /**
     * Modelo A: el pedido se persiste para obtener su id y luego se reserva stock
     * de forma síncrona contra catalog. Si la reserva falla, la transacción
     * completa se revierte y no se publica ningún comando a RabbitMQ.
     */
    @Transactional
    public Order crearPedido(Order nuevoPedido, String cuenta, String accessToken) {
        nuevoPedido.setClientId(cuenta);
        if (nuevoPedido.getItems() == null || nuevoPedido.getItems().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe incluir al menos un ítem");
        }

        nuevoPedido.setStatus(EstadoPedido.CREADO.name());
        BigDecimal totalCalculado = BigDecimal.ZERO;

        for (OrderItem item : nuevoPedido.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                throw new IllegalArgumentException("El precio unitario no puede ser negativo");
            }

            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setSubtotal(subtotal);
            item.setOrder(nuevoPedido);
            totalCalculado = totalCalculado.add(subtotal);
        }

        nuevoPedido.setTotalAmount(totalCalculado);
        Order guardado = orderRepository.saveAndFlush(nuevoPedido);

        StockReservationResponse reserva = catalogStockClient.reservar(
                guardado.getId(), guardado.getItems(), accessToken);
        log.info("Stock reservado orderId={} operation={} items={}",
                guardado.getId(), reserva.operation(), reserva.items());

        eventPublisher.publishEvent(new PedidoPersistidoEvent(
                guardado.getId(), guardado.getClientId(), guardado.getStatus()));
        return guardado;
    }

    @Transactional
    public Order cambiarEstado(Long id, String nuevoEstado, String accessToken) {
        EstadoPedido nuevo = pedidoPolicy.parse(nuevoEstado);
        Order pedido = obtenerPorId(id);
        EstadoPedido actual = pedidoPolicy.parse(pedido.getStatus());

        if (nuevo == actual) {
            return pedido;
        }

        pedidoPolicy.validarTransicion(actual, nuevo);

        if (nuevo == EstadoPedido.CANCELADO && !pedido.getItems().isEmpty()) {
            StockReservationResponse liberacion = catalogStockClient.liberar(
                    pedido.getId(), pedido.getItems(), accessToken);
            log.info("Stock liberado orderId={} operation={} items={}",
                    pedido.getId(), liberacion.operation(), liberacion.items());
        }

        pedido.setStatus(nuevo.name());
        Order guardado = orderRepository.save(pedido);
        eventPublisher.publishEvent(new PedidoPersistidoEvent(
                guardado.getId(), guardado.getClientId(), guardado.getStatus()));
        return guardado;
    }
}
