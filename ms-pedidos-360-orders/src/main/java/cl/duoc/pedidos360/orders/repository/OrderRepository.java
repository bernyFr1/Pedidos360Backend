package cl.duoc.pedidos360.orders.repository;

import cl.duoc.pedidos360.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClientId(String clientId);

    List<Order> findByStatus(String status);
}
