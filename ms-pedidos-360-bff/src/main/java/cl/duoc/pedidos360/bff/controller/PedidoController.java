package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.OrdersClient;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PedidoController {

    private final OrdersClient ordersClient;

    public PedidoController(OrdersClient ordersClient) {
        this.ordersClient = ordersClient;
    }

    @GetMapping("/publico")
    public Map<String, String> publico() {
        return Map.of("mensaje", "Endpoint público activo sin autenticación");
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<List<Object>> obtenerPedidos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ordersClient.listarPedidos(jwt.getTokenValue()));
    }

    @PostMapping("/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Object> crearPedido(@RequestBody Object pedido, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201).body(ordersClient.crearPedido(pedido, jwt.getTokenValue()));
    }
}
