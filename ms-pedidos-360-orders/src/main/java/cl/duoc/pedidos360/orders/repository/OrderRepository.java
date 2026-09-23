package cl.duoc.pedidos360.orders.repository;

import cl.duoc.pedidos360.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByClientId(String clientId);

    @EntityGraph(attributePaths = "items")
    List<Order> findByStatus(String status);

    @EntityGraph(attributePaths = "items")
    @Override
    List<Order> findAll();
}
