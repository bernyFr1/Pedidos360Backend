package cl.duoc.pedidos360.bff.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrdersClient {

    private final RestClient restClient;

    public OrdersClient(@Value("${services.orders.url}") String ordersUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(ordersUrl)
                .build();
    }

    public List<Map<String, Object>> getOrders() {
        return this.restClient.get()
                .uri("/orders")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }
}