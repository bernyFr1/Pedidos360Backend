package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.domain.PedidoPolicy;
import cl.duoc.pedidos360.orders.dto.PedidoComando;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class PedidoPersistidoListener {

    private final PedidoEventPublisher publisher;
    private final PedidoPolicy pedidoPolicy;

    public PedidoPersistidoListener(PedidoEventPublisher publisher, PedidoPolicy pedidoPolicy) {
        this.publisher = publisher;
        this.pedidoPolicy = pedidoPolicy;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alPersistirPedido(PedidoPersistidoEvent event) {
        String modo = "CREADO".equals(event.estado()) ? "direct" : "topic";
        List<String> dominios = pedidoPolicy.dominiosPara(event.estado());
        publisher.publicarComandos(new PedidoComando(
                event.pedidoId(),
                event.clienteEmail(),
                event.estado(),
                false,
                false,
                false,
                modo,
                dominios
        ));
    }
}
