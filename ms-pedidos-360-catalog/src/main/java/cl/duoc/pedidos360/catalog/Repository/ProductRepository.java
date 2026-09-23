package cl.duoc.pedidos360.catalog.Repository;

import cl.duoc.pedidos360.catalog.Entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
