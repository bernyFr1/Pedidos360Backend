package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.KafkaAdminClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/kafka")
@PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_ADMIN')")
public class KafkaAdminController {

    private final KafkaAdminClient kafkaAdminClient;

    public KafkaAdminController(KafkaAdminClient kafkaAdminClient) {
        this.kafkaAdminClient = kafkaAdminClient;
    }

    @GetMapping("/cluster")
    public ResponseEntity<Object> cluster(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(kafkaAdminClient.cluster(jwt.getTokenValue()));
    }

    @GetMapping("/topics")
    public ResponseEntity<Object> topics(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(kafkaAdminClient.topics(jwt.getTokenValue()));
    }

    @GetMapping("/topics/{name}")
    public ResponseEntity<Object> topic(
            @PathVariable String name,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(kafkaAdminClient.topic(name, jwt.getTokenValue()));
    }

    @PostMapping("/topics")
    public ResponseEntity<Void> crear(
            @RequestBody Object request,
            @AuthenticationPrincipal Jwt jwt) {
        kafkaAdminClient.crear(request, jwt.getTokenValue());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/topics/{name}/configs")
    public ResponseEntity<Void> actualizarConfiguracion(
            @PathVariable String name,
            @RequestBody Object request,
            @AuthenticationPrincipal Jwt jwt) {
        kafkaAdminClient.actualizarConfiguracion(name, request, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String name,
            @AuthenticationPrincipal Jwt jwt) {
        kafkaAdminClient.eliminar(name, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consumer-groups")
    public ResponseEntity<Object> groups(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(kafkaAdminClient.groups(jwt.getTokenValue()));
    }

    @GetMapping("/consumer-groups/{groupId}")
    public ResponseEntity<Object> group(
            @PathVariable String groupId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(kafkaAdminClient.group(groupId, jwt.getTokenValue()));
    }
}
