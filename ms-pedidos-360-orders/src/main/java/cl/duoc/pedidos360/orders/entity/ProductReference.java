package cl.duoc.pedidos360.orders.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "PRODUCTS")
public class ProductReference {

    @Id
    private Long id;

    protected ProductReference() {
    }

    public ProductReference(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
