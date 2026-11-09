package cl.duoc.pedidos360.adminmq.monitoring.controller;

import cl.duoc.pedidos360.adminmq.monitoring.dto.DlqMessageResponse;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RabbitMqOverviewResponse;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RabbitQueueMetricsResponse;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RequeueRequest;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RequeueResponse;
import cl.duoc.pedidos360.adminmq.monitoring.service.RabbitMqMonitoringService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/metrics/rabbitmq")
@PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_ADMIN')")
public class RabbitMqMonitoringController {

    private final RabbitMqMonitoringService monitoringService;

    public RabbitMqMonitoringController(RabbitMqMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/overview")
    public RabbitMqOverviewResponse overview() {
        return monitoringService.obtenerOverview();
    }

    @GetMapping("/queues")
    public List<RabbitQueueMetricsResponse> queues() {
        return monitoringService.obtenerColas();
    }

    @GetMapping("/dlq/messages")
    public List<DlqMessageResponse> dlqMessages(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) Integer limit) {
        return monitoringService.obtenerMensajesDlq(queue, limit);
    }

    @PostMapping("/dlq/requeue")
    public ResponseEntity<RequeueResponse> requeue(@Valid @RequestBody RequeueRequest request) {
        return ResponseEntity.ok(monitoringService.reencolar(request));
    }
}
