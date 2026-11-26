package cl.duoc.pedidos360.audit.service;

import cl.duoc.pedidos360.audit.kafka.PedidoEventoKafka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final int MAX_EVENTOS = 100;

    private final AtomicLong totalEventos = new AtomicLong();
    private final CopyOnWriteArrayList<String> ultimosEventos = new CopyOnWriteArrayList<>();

    public void registrar(PedidoEventoKafka evento) {
        totalEventos.incrementAndGet();

        String resumen = String.format(
                "eventId=%s pedidoId=%s estado=%s cliente=%s total=%s traceId=%s",
                evento.eventId(),
                evento.pedidoId(),
                evento.status(),
                evento.clientId(),
                evento.totalAmount(),
                evento.traceId());

        ultimosEventos.add(resumen);
        if (ultimosEventos.size() > MAX_EVENTOS) {
            ultimosEventos.remove(0);
        }

        log.info("AUDIT {}", resumen);
    }

    public long totalEventos() {
        return totalEventos.get();
    }

    public List<String> ultimosEventos() {
        return List.copyOf(ultimosEventos);
    }
}
