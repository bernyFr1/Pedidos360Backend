package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.CatalogClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class BffCatalogController {

    private final CatalogClient catalogClient;

    public BffCatalogController(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    // Todos los usuarios autenticados pueden ver el catálogo
    @GetMapping("/products")
    public ResponseEntity<List<Object>> getProducts() {
        List<Object> products = this.catalogClient.getCatalog();
        return ResponseEntity.ok(products);
    }
}