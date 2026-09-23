package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.CatalogClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogClient catalogClient;

    public CatalogController(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<List<Object>> getProducts(@AuthenticationPrincipal Jwt jwt) {
        List<Object> products = this.catalogClient.getCatalog(jwt.getTokenValue());
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Object> getProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(this.catalogClient.getProduct(id, jwt.getTokenValue()));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Object> createProduct(
            @RequestBody Object product,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201)
                .body(this.catalogClient.createProduct(product, jwt.getTokenValue()));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Object> updateProduct(
            @PathVariable Long id,
            @RequestBody Object product,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(this.catalogClient.updateProduct(id, product, jwt.getTokenValue()));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMINISTRADOR', 'ROLE_OPERADOR')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        this.catalogClient.deleteProduct(id, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }
}