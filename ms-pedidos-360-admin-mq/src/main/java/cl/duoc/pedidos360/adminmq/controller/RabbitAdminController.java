package cl.duoc.pedidos360.adminmq.controller;

import cl.duoc.pedidos360.adminmq.dto.BindingRequest;
import cl.duoc.pedidos360.adminmq.dto.ExchangeRequest;
import cl.duoc.pedidos360.adminmq.dto.QueueRequest;
import cl.duoc.pedidos360.adminmq.dto.QueueResponse;
import cl.duoc.pedidos360.adminmq.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping("/queues")
    public ResponseEntity<QueueResponse> crearCola(@Valid @RequestBody QueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.crearCola(request));
    }

    @GetMapping("/queues/{name}")
    public QueueResponse obtenerCola(@PathVariable String name) {
        return rabbitAdminService.obtenerCola(name);
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String name) {
        rabbitAdminService.eliminarCola(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Void> crearExchange(@Valid @RequestBody ExchangeRequest request) {
        rabbitAdminService.crearExchange(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String name) {
        rabbitAdminService.eliminarExchange(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings")
    public ResponseEntity<Void> crearBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdminService.crearBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(
            @RequestParam String queue,
            @RequestParam String exchange,
            @RequestParam(required = false, defaultValue = "") String routingKey) {
        if (queue.isBlank() || exchange.isBlank()) {
            throw new IllegalArgumentException("queue y exchange son obligatorios");
        }
        rabbitAdminService.eliminarBinding(new BindingRequest(queue, exchange, routingKey));
        return ResponseEntity.noContent().build();
    }
}
