package cl.duoc.pedidos360.catalog.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Registra el cambio de stock después de confirmar la transacción.
 * Es el punto de extensión para conectar el productor Kafka más adelante.
 */
@Component
public class StockEventLogger {

    private static final Logger log = LoggerFactory.getLogger(StockEventLogger.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void registrar(StockChangedEvent event) {
        event.items().forEach(item -> log.info(
                "STOCK {} orderId={} productId={} previous={} delta={} new={}",
                event.operation(),
                event.orderId(),
                item.productId(),
                item.previousStock(),
                item.delta(),
                item.newStock()));
    }
}
