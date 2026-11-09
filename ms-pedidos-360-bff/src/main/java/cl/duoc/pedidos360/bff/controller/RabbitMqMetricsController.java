package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.RabbitMqMetricsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics/rabbitmq")
@PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_ADMIN')")
public class RabbitMqMetricsController {

    private final RabbitMqMetricsClient metricsClient;

    public RabbitMqMetricsController(RabbitMqMetricsClient metricsClient) {
        this.metricsClient = metricsClient;
    }

    @GetMapping("/overview")
    public ResponseEntity<Object> overview(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(metricsClient.overview(jwt.getTokenValue()));
    }

    @GetMapping("/queues")
    public ResponseEntity<Object> queues(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(metricsClient.queues(jwt.getTokenValue()));
    }

    @GetMapping("/dlq/messages")
    public ResponseEntity<Object> dlqMessages(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(metricsClient.dlqMessages(queue, limit, jwt.getTokenValue()));
    }

    @PostMapping("/dlq/requeue")
    public ResponseEntity<Object> requeue(
            @RequestBody Object request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(metricsClient.requeue(request, jwt.getTokenValue()));
    }
}
