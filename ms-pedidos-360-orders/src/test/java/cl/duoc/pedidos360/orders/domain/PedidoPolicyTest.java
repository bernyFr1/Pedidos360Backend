package cl.duoc.pedidos360.orders.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoPolicyTest {

    private final PedidoPolicy policy = new PedidoPolicy();

    @Test
    void permiteTransicionesDelFlujo() {
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.CREADO, EstadoPedido.ACEPTADO));
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.ACEPTADO, EstadoPedido.EN_PREPARACION));
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.EN_PREPARACION, EstadoPedido.DESPACHADO));
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.DESPACHADO, EstadoPedido.ENTREGADO));
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.CREADO, EstadoPedido.CANCELADO));
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.ACEPTADO, EstadoPedido.CANCELADO));
        assertDoesNotThrow(() -> policy.validarTransicion(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO));
    }

    @Test
    void rechazaTransicionesInvalidas() {
        assertThrows(IllegalStateException.class,
                () -> policy.validarTransicion(EstadoPedido.CREADO, EstadoPedido.ENTREGADO));
        assertThrows(IllegalStateException.class,
                () -> policy.validarTransicion(EstadoPedido.CREADO, EstadoPedido.DESPACHADO));
        assertThrows(IllegalStateException.class,
                () -> policy.validarTransicion(EstadoPedido.ACEPTADO, EstadoPedido.DESPACHADO));
        assertThrows(IllegalStateException.class,
                () -> policy.validarTransicion(EstadoPedido.DESPACHADO, EstadoPedido.CANCELADO));
        assertThrows(IllegalStateException.class,
                () -> policy.validarTransicion(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO));
        assertThrows(IllegalStateException.class,
                () -> policy.validarTransicion(EstadoPedido.CANCELADO, EstadoPedido.ACEPTADO));
    }

    @Test
    void defineLosDominiosPorEstado() {
        assertEquals(List.of("email"), policy.dominiosPara(EstadoPedido.CREADO));
        assertEquals(List.of("email", "kitchen"), policy.dominiosPara(EstadoPedido.ACEPTADO));
        assertEquals(List.of("email", "kitchen"), policy.dominiosPara(EstadoPedido.EN_PREPARACION));
        assertEquals(List.of("email", "invoice"), policy.dominiosPara(EstadoPedido.DESPACHADO));
        assertEquals(List.of("email", "invoice"), policy.dominiosPara(EstadoPedido.ENTREGADO));
        assertEquals(List.of("email", "kitchen", "invoice"), policy.dominiosPara(EstadoPedido.CANCELADO));
    }

    @Test
    void parseNormalizaYRechazaEstadosInvalidos() {
        assertEquals(EstadoPedido.ACEPTADO, policy.parse(" aceptado "));
        assertThrows(IllegalArgumentException.class, () -> policy.parse(null));
        assertThrows(IllegalArgumentException.class, () -> policy.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> policy.parse("ENVIADO"));
    }
}
