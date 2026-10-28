package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.domain.EstadoPedido;
import cl.duoc.pedidos360.orders.domain.PedidoPolicy;
import cl.duoc.pedidos360.orders.dto.LabPedidoRequest;
import cl.duoc.pedidos360.orders.dto.LabPedidoResponse;
import cl.duoc.pedidos360.orders.dto.PedidoComando;
import cl.duoc.pedidos360.orders.dto.ResultadoPublicacion;
import cl.duoc.pedidos360.orders.messaging.PedidoEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/lab/pedidos")
public class LabPedidoController {

    private final PedidoEventPublisher publisher;
    private final PedidoPolicy pedidoPolicy;

    public LabPedidoController(PedidoEventPublisher publisher, PedidoPolicy pedidoPolicy) {
        this.publisher = publisher;
        this.pedidoPolicy = pedidoPolicy;
    }

    @PostMapping("/aceptar")
    public ResponseEntity<LabPedidoResponse> aceptar(@Valid @RequestBody LabPedidoRequest request) {
        EstadoPedido estado = pedidoPolicy.parse(
                request.estado() == null ? EstadoPedido.ACEPTADO.name() : request.estado());
        String exchange = request.exchange() == null
                ? "direct"
                : request.exchange().toLowerCase(Locale.ROOT);
        List<String> dominios = pedidoPolicy.dominiosPara(estado);

        ResultadoPublicacion resultado = publisher.publicarComandos(new PedidoComando(
                request.pedidoId(),
                request.clienteEmail(),
                estado.name(),
                request.falloEmail(),
                request.falloCocina(),
                request.falloFactura(),
                exchange,
                dominios
        ));

        return ResponseEntity.accepted().body(new LabPedidoResponse(
                estado.name(),
                request.pedidoId(),
                resultado.correlationId(),
                resultado.comandos()
        ));
    }
}
