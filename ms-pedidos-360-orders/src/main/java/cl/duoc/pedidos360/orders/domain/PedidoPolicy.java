package cl.duoc.pedidos360.orders.domain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PedidoPolicy {

    private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES = Map.of(
            EstadoPedido.CREADO, Set.of(EstadoPedido.ACEPTADO, EstadoPedido.CANCELADO),
            EstadoPedido.ACEPTADO, Set.of(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO),
            EstadoPedido.EN_PREPARACION, Set.of(EstadoPedido.DESPACHADO, EstadoPedido.CANCELADO),
            EstadoPedido.DESPACHADO, Set.of(EstadoPedido.ENTREGADO),
            EstadoPedido.ENTREGADO, Set.of(),
            EstadoPedido.CANCELADO, Set.of()
    );

    private static final Map<EstadoPedido, List<String>> DOMINIOS = Map.of(
            EstadoPedido.CREADO, List.of("email"),
            EstadoPedido.ACEPTADO, List.of("email", "kitchen"),
            EstadoPedido.EN_PREPARACION, List.of("email", "kitchen"),
            EstadoPedido.DESPACHADO, List.of("email", "invoice"),
            EstadoPedido.ENTREGADO, List.of("email", "invoice"),
            EstadoPedido.CANCELADO, List.of("email", "kitchen", "invoice")
    );

    public EstadoPedido parse(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        try {
            return EstadoPedido.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Estado no válido: " + estado, exception);
        }
    }

    public void validarTransicion(EstadoPedido actual, EstadoPedido nuevo) {
        if (actual == nuevo) {
            return;
        }
        if (!TRANSICIONES.getOrDefault(actual, Set.of()).contains(nuevo)) {
            throw new IllegalStateException("Transición no permitida: " + actual + " -> " + nuevo);
        }
    }

    public List<String> dominiosPara(EstadoPedido estado) {
        return DOMINIOS.getOrDefault(estado, List.of());
    }

    public List<String> dominiosPara(String estado) {
        return dominiosPara(parse(estado));
    }
}
