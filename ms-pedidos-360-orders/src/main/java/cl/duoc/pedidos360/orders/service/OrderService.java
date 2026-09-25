package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import cl.duoc.pedidos360.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO", "ENTREGADO", "CANCELADO");

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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

    @Transactional
    public Order crearPedido(Order nuevoPedido, String cuenta) {
        nuevoPedido.setClientId(cuenta);
        if (nuevoPedido.getItems() == null || nuevoPedido.getItems().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe incluir al menos un ítem");
        }

        nuevoPedido.setStatus("CREADO");
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
        return orderRepository.save(nuevoPedido);
    }

    @Transactional
    public Order cambiarEstado(Long id, String nuevoEstado) {
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        String estadoUpper = nuevoEstado.toUpperCase();
        if (!ESTADOS_VALIDOS.contains(estadoUpper)) {
            throw new IllegalArgumentException("Estado no válido: " + nuevoEstado);
        }

        Order pedido = obtenerPorId(id);
        String estadoActual = pedido.getStatus();

        if ("DESPACHADO".equals(estadoUpper) && "CREADO".equals(estadoActual)) {
            throw new IllegalStateException("Un pedido CREADO debe ser ACEPTADO antes de ser DESPACHADO");
        }

        if ("ENTREGADO".equals(estadoActual) || "CANCELADO".equals(estadoActual)) {
            throw new IllegalStateException("No se puede cambiar el estado de un pedido " + estadoActual);
        }

        pedido.setStatus(estadoUpper);
        return orderRepository.save(pedido);
    }
}
