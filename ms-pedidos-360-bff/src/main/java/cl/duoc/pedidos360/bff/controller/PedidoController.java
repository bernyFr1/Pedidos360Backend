package cl.duoc.pedidos360.bff.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PedidoController {

    @GetMapping("/publico")
    public Map<String, String> publico() {
        return Map.of("mensaje", "Endpoint público activo sin autenticación");
    }

    @GetMapping("/pedidos")
    public Map<String, Object> obtenerPedidos(@AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt.getClaimAsString("preferred_username");
        if (usuario == null) {
            usuario = jwt.getSubject();
        }

        return Map.of(
                "mensaje", "Acceso autorizado por Spring Security",
                "usuario", usuario,
                "tenantId", jwt.getClaimAsString("tid"),
                "pedidos", List.of(
                        Map.of("id", "PED-001", "cliente", "Panadería Central", "monto", 15000),
                        Map.of("id", "PED-002", "cliente", "Café Express", "monto", 22000)));
    }
}
