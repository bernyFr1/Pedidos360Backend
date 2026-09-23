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

    public List<Object> getCatalog() {
        return this.restClient.get()
                .uri("/products")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Object>>() {});
    }
}