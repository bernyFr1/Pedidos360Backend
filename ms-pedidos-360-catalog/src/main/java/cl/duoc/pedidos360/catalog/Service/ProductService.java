package cl.duoc.pedidos360.catalog.Service;

import cl.duoc.pedidos360.catalog.Entity.Product;
import cl.duoc.pedidos360.catalog.Repository.ProductRepository;
import cl.duoc.pedidos360.catalog.dto.StockChangeItem;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
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
        Product guardado = productRepository.save(product);
        publicarEvento(null, "AJUSTE", guardado, 0, guardado.getStock());
        return guardado;
    }

    @Transactional
    public Product actualizarProducto(Long id, Product detalles) {
        Product existente = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado con ID: " + id));
        int stockAnterior = existente.getStock() == null ? 0 : existente.getStock();

        existente.setName(detalles.getName());
        existente.setDescription(detalles.getDescription());
        existente.setPrice(detalles.getPrice());
        existente.setStock(detalles.getStock());
        validarProducto(existente);

        Product guardado = productRepository.save(existente);

        if (!Objects.equals(stockAnterior, guardado.getStock())) {
            publicarEvento(null, "AJUSTE", guardado, stockAnterior, guardado.getStock());
        }

        return guardado;
    }

    @Transactional
    public void reducirStock(Long id, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a reducir debe ser mayor que cero");
        }

        Product producto = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado con ID: " + id));
        if (producto.getStock() == null || producto.getStock() < cantidad) {
            throw new IllegalStateException("Stock insuficiente para el producto: " + producto.getName());
        }

        int stockAnterior = producto.getStock();
        producto.setStock(stockAnterior - cantidad);
        Product guardado = productRepository.save(producto);
        publicarEvento(null, "AJUSTE", guardado, stockAnterior, guardado.getStock());
    }

    @Transactional
    public void eliminarProducto(Long id) {
        Product producto = obtenerPorId(id);
        productRepository.delete(producto);
    }

    private void publicarEvento(Long orderId, String operation, Product producto, int anterior, int nuevo) {
        eventPublisher.publishEvent(new StockChangedEvent(
                orderId,
                operation,
                List.of(new StockChangeItem(
                        producto.getId(),
                        Math.abs(nuevo - anterior),
                        anterior,
                        nuevo,
                        nuevo - anterior,
                        producto.getPrice())),
                Instant.now()));
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
