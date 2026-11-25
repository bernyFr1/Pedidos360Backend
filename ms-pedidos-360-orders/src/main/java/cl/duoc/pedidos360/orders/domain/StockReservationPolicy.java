package cl.duoc.pedidos360.orders.domain;

import cl.duoc.pedidos360.orders.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Protege la liberación de stock de pedidos creados antes de que existiera
 * la reserva. Sin esta regla, cancelar un pedido antiguo devolvería stock
 * que nunca fue reservado.
 */
@Component
public class StockReservationPolicy {

    private final LocalDateTime reservationStart;

    public StockReservationPolicy(@Value("${app.stock.reservation-start:}") String reservationStart) {
        this.reservationStart = reservationStart == null || reservationStart.isBlank()
                ? null
                : LocalDateTime.parse(reservationStart.trim());
    }

    public boolean debeLiberar(Order order) {
        if (reservationStart == null) {
            return true;
        }

        LocalDateTime createdAt = order.getCreatedAt();
        return createdAt == null || !createdAt.isBefore(reservationStart);
    }
}
