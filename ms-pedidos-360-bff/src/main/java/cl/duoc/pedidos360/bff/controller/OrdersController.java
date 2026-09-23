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
    public ResponseEntity<List<Object>> listarPedidos(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean esClientePuro = roles != null && roles.contains("ROLE_CLIENTE")
                && !roles.contains("ROLE_ADMINISTRADOR") && !roles.contains("ROLE_OPERADOR");

        // Regla de negocio: Si es Cliente, solo ve sus propios pedidos (se filtra por
        // sub / preferred_username)
        if (esClientePuro) {
            String clientId = jwt.getClaimAsString("preferred_username");
            if (clientId == null)
                clientId = jwt.getSubject();
            return ResponseEntity.ok(ordersClient.listarPedidos(clientId, jwt.getTokenValue()));
        }

        // Operador y Admin ven todos los pedidos
        return ResponseEntity.ok(ordersClient.listarPedidos(null, jwt.getTokenValue()));
    }

    @PostMapping
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