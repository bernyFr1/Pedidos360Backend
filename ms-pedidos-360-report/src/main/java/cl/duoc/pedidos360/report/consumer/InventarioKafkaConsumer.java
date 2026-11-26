package cl.duoc.pedidos360.report.consumer;

import cl.duoc.pedidos360.report.kafka.InventarioEventoKafka;
import cl.duoc.pedidos360.report.service.ReporteService;
import cl.duoc.pedidos360.report.validation.KafkaEventValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventarioKafkaConsumer {

    private final ReporteService reporteService;
    private final KafkaEventValidator validator;

    public InventarioKafkaConsumer(ReporteService reporteService, KafkaEventValidator validator) {
        this.reporteService = reporteService;
        this.validator = validator;
    }

    @KafkaListener(
            topics = "inventario-eventos",
            groupId = "report-inventario-group",
            properties = "spring.json.value.default.type=cl.duoc.pedidos360.report.kafka.InventarioEventoKafka")
    public void consumir(InventarioEventoKafka evento) {
        validator.validar(evento);
        reporteService.procesarInventario(evento);
    }
}
