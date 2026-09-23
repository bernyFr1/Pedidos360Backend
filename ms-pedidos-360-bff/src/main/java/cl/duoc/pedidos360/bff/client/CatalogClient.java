package cl.duoc.pedidos360.bff.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(@Value("${services.catalog.url}") String catalogUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogUrl)
                .build();
    }

    public List<Object> getCatalog(String accessToken) {
        return this.restClient.get()
                .uri("/api/catalog/products")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Object>>() {
                });
    }

    public Object getProduct(Long id, String accessToken) {
        return this.restClient.get()
                .uri("/api/catalog/products/{id}", id)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object createProduct(Object product, String accessToken) {
        return this.restClient.post()
                .uri("/api/catalog/products")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(product)
                .retrieve()
                .body(Object.class);
    }

    public Object updateProduct(Long id, Object product, String accessToken) {
        return this.restClient.put()
                .uri("/api/catalog/products/{id}", id)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(product)
                .retrieve()
                .body(Object.class);
    }

    public void deleteProduct(Long id, String accessToken) {
        this.restClient.delete()
                .uri("/api/catalog/products/{id}", id)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }
}