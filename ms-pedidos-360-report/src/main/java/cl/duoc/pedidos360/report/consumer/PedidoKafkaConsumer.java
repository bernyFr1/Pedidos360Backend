package cl.duoc.pedidos360.report.consumer;

import cl.duoc.pedidos360.report.kafka.PedidoEventoKafka;
import cl.duoc.pedidos360.report.service.ReporteService;
import cl.duoc.pedidos360.report.validation.KafkaEventValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoKafkaConsumer {

    private final ReporteService reporteService;
    private final KafkaEventValidator validator;

    public PedidoKafkaConsumer(ReporteService reporteService, KafkaEventValidator validator) {
        this.reporteService = reporteService;
        this.validator = validator;
    }

    @KafkaListener(
            topics = "pedidos-eventos",
            groupId = "report-pedidos-group",
            properties = "spring.json.value.default.type=cl.duoc.pedidos360.report.kafka.PedidoEventoKafka")
    public void consumir(PedidoEventoKafka evento) {
        validator.validar(evento);
        reporteService.procesarPedido(evento);
    }
}
