package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.OrdersClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrdersController {

    private final OrdersClient ordersClient;

    public OrdersController(OrdersClient ordersClient) {
        this.ordersClient = ordersClient;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<List<Object>> listarPedidos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ordersClient.listarPedidos(jwt.getTokenValue()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Object> crearPedido(@RequestBody Object pedido, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201).body(ordersClient.crearPedido(pedido, jwt.getTokenValue()));
    }

    // Solo el Operador (o Administrador) puede cambiar el estado de un pedido
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Object> cambiarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ordersClient.cambiarEstado(id, body.get("status"), jwt.getTokenValue()));
    }
}