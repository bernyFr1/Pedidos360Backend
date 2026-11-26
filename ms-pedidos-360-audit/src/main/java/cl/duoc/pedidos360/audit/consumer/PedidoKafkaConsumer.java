package cl.duoc.pedidos360.audit.consumer;

import cl.duoc.pedidos360.audit.kafka.PedidoEventoKafka;
import cl.duoc.pedidos360.audit.service.AuditoriaService;
import cl.duoc.pedidos360.audit.validation.KafkaEventValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoKafkaConsumer {

    private final AuditoriaService auditoriaService;
    private final KafkaEventValidator validator;

    public PedidoKafkaConsumer(AuditoriaService auditoriaService, KafkaEventValidator validator) {
        this.auditoriaService = auditoriaService;
        this.validator = validator;
    }

    @KafkaListener(
            topics = "pedidos-eventos",
            groupId = "audit-pedidos-group",
            properties = "spring.json.value.default.type=cl.duoc.pedidos360.audit.kafka.PedidoEventoKafka")
    public void consumir(PedidoEventoKafka evento) {
        validator.validar(evento);
        auditoriaService.registrar(evento);
    }
}
