package cl.duoc.pedidos360.catalog.Service;

import cl.duoc.pedidos360.catalog.Entity.Product;
import cl.duoc.pedidos360.catalog.Repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> obtenerTodos() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product obtenerPorId(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado con ID: " + id));
    }

    @Transactional
    public Product crearProducto(Product product) {
        product.setId(null);
        validarProducto(product);
        return productRepository.save(product);
    }

    @Transactional
    public Product actualizarProducto(Long id, Product detalles) {
        Product existente = obtenerPorId(id);
        existente.setName(detalles.getName());
        existente.setDescription(detalles.getDescription());
        existente.setPrice(detalles.getPrice());
        existente.setStock(detalles.getStock());
        validarProducto(existente);
        return productRepository.save(existente);
    }

    @Transactional
    public void reducirStock(Long id, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a reducir debe ser mayor que cero");
        }

        Product producto = obtenerPorId(id);
        if (producto.getStock() == null || producto.getStock() < cantidad) {
            throw new IllegalStateException("Stock insuficiente para el producto: " + producto.getName());
        }
        producto.setStock(producto.getStock() - cantidad);
        productRepository.save(producto);
    }

    @Transactional
    public void eliminarProducto(Long id) {
        Product producto = obtenerPorId(id);
        productRepository.delete(producto);
    }

    private void validarProducto(Product product) {
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio del producto debe ser mayor o igual a cero");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
    }
}
