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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

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
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return ResponseEntity.ok(orderService.obtenerPedidos(cuentaDesde(jwt), puedeVerTodos(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Order> obtenerPedido(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return ResponseEntity.ok(orderService.obtenerPorId(id, cuentaDesde(jwt), puedeVerTodos(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Order> crearPedido(
            @RequestBody Order pedido, @AuthenticationPrincipal Jwt jwt) {
        Order nuevo = orderService.crearPedido(pedido, cuentaDesde(jwt));
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

    private String cuentaDesde(Jwt jwt) {
        String cuenta = jwt.getClaimAsString("preferred_username");
        return cuenta != null ? cuenta : jwt.getSubject();
    }

    private boolean puedeVerTodos(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OPERADOR".equals(authority.getAuthority())
                        || "ROLE_ADMINISTRADOR".equals(authority.getAuthority()));
    }
}
