package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<List<Order>> listarPedidos(
            @RequestParam(required = false) String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            return ResponseEntity.ok(orderService.obtenerPorCliente(clientId));
        }
        return ResponseEntity.ok(orderService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> obtenerPedido(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Order> crearPedido(@RequestBody Order pedido) {
        Order nuevo = orderService.crearPedido(pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Order> cambiarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String nuevoEstado = body.get("status");
        if (nuevoEstado == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(orderService.cambiarEstado(id, nuevoEstado));
    }
}
