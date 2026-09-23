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
}