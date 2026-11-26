package cl.duoc.pedidos360.report.service;

import cl.duoc.pedidos360.report.kafka.InventarioEventoKafka;
import cl.duoc.pedidos360.report.kafka.PedidoEventoKafka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Vista materializada en memoria para la demo de reportes.
 * En producción esto se reemplaza por una tabla/read model, pero mantiene
 * la separación entre mensajería y lógica de negocio.
 */
@Service
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    private final AtomicLong pedidosProcesados = new AtomicLong();
    private final AtomicLong inventarioProcesado = new AtomicLong();
    private final ConcurrentHashMap<Long, Integer> stockPorProducto = new ConcurrentHashMap<>();

    public void procesarPedido(PedidoEventoKafka evento) {
        pedidosProcesados.incrementAndGet();
        log.info("REPORT pedido eventId={} pedidoId={} estado={} total={} items={} traceId={}",
                evento.eventId(),
                evento.pedidoId(),
                evento.status(),
                evento.totalAmount(),
                evento.items().size(),
                evento.traceId());
    }

    public void procesarInventario(InventarioEventoKafka evento) {
        inventarioProcesado.incrementAndGet();
        stockPorProducto.put(evento.productId(), evento.newStock());
        log.info("REPORT stock eventId={} productId={} previous={} delta={} new={} traceId={}",
                evento.eventId(),
                evento.productId(),
                evento.previousStock(),
                evento.delta(),
                evento.newStock(),
                evento.traceId());
    }

    public long pedidosProcesados() {
        return pedidosProcesados.get();
    }

    public long inventarioProcesado() {
        return inventarioProcesado.get();
    }

    public Map<Long, Integer> stockActual() {
        return Map.copyOf(stockPorProducto);
    }
}
