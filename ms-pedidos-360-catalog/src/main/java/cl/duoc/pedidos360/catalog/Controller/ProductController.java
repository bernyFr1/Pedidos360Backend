package cl.duoc.pedidos360.catalog.Controller;

import cl.duoc.pedidos360.catalog.Entity.Product;
import cl.duoc.pedidos360.catalog.Service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/catalog")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public List<Product> listarProductos() {
        return productService.obtenerTodos();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> obtenerProducto(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(productService.obtenerPorId(id));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/products")
    public ResponseEntity<Product> crearProducto(@RequestBody Product product) {
        Product productoCreado = productService.crearProducto(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(productoCreado);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> actualizarProducto(
            @PathVariable Long id,
            @RequestBody Product product) {
        try {
            return ResponseEntity.ok(productService.actualizarProducto(id, product));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
        try {
            productService.eliminarProducto(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
