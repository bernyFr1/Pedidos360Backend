package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.RabbitAdminClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rabbit")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminRabbitController {

    private final RabbitAdminClient rabbitAdminClient;

    public AdminRabbitController(RabbitAdminClient rabbitAdminClient) {
        this.rabbitAdminClient = rabbitAdminClient;
    }

    @PostMapping("/queues")
    public ResponseEntity<Object> crearCola(
            @RequestBody Object request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rabbitAdminClient.crearCola(request, jwt.getTokenValue()));
    }

    @GetMapping("/queues/{name}")
    public ResponseEntity<Object> obtenerCola(
            @PathVariable String name,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(rabbitAdminClient.obtenerCola(name, jwt.getTokenValue()));
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> eliminarCola(
            @PathVariable String name,
            @AuthenticationPrincipal Jwt jwt) {
        rabbitAdminClient.eliminarCola(name, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Void> crearExchange(
            @RequestBody Object request,
            @AuthenticationPrincipal Jwt jwt) {
        rabbitAdminClient.crearExchange(request, jwt.getTokenValue());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> eliminarExchange(
            @PathVariable String name,
            @AuthenticationPrincipal Jwt jwt) {
        rabbitAdminClient.eliminarExchange(name, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings")
    public ResponseEntity<Void> crearBinding(
            @RequestBody Object request,
            @AuthenticationPrincipal Jwt jwt) {
        rabbitAdminClient.crearBinding(request, jwt.getTokenValue());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(
            @RequestParam String queue,
            @RequestParam String exchange,
            @RequestParam(required = false, defaultValue = "") String routingKey,
            @AuthenticationPrincipal Jwt jwt) {
        rabbitAdminClient.eliminarBinding(queue, exchange, routingKey, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }
}
