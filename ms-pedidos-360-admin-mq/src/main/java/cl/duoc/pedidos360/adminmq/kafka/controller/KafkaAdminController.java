package cl.duoc.pedidos360.adminmq.kafka.controller;

import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaClusterResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaConsumerGroupDetailResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaConsumerGroupResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicConfigRequest;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicRequest;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicResponse;
import cl.duoc.pedidos360.adminmq.kafka.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/kafka")
@PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_ADMIN')")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    @GetMapping("/cluster")
    public KafkaClusterResponse cluster() {
        return kafkaAdminService.obtenerCluster();
    }

    @GetMapping("/topics")
    public List<KafkaTopicResponse> topics() {
        return kafkaAdminService.listarTopicos();
    }

    @GetMapping("/topics/{name}")
    public KafkaTopicResponse topic(@PathVariable String name) {
        return kafkaAdminService.obtenerTopico(name);
    }

    @PostMapping("/topics")
    public ResponseEntity<Void> crear(@Valid @RequestBody KafkaTopicRequest request) {
        kafkaAdminService.crearTopico(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/topics/{name}/configs")
    public ResponseEntity<Void> actualizarConfiguracion(
            @PathVariable String name,
            @Valid @RequestBody KafkaTopicConfigRequest request) {
        kafkaAdminService.actualizarConfiguracion(name, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> eliminar(@PathVariable String name) {
        kafkaAdminService.eliminarTopico(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consumer-groups")
    public List<KafkaConsumerGroupResponse> groups() {
        return kafkaAdminService.listarGrupos();
    }

    @GetMapping("/consumer-groups/{groupId}")
    public KafkaConsumerGroupDetailResponse group(@PathVariable String groupId) {
        return kafkaAdminService.obtenerGrupo(groupId);
    }
}
