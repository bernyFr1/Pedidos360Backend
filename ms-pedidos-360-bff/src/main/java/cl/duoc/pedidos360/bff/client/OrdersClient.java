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

    public List<Object> listarPedidos(String accessToken) {
        return this.restClient.get()
                .uri("/api/orders")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Object>>() {
                });
    }

    public Object crearPedido(Object pedido, String accessToken) {
        return this.restClient.post()
                .uri("/api/orders")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(pedido)
                .retrieve()
                .body(Object.class);
    }

    public Object cambiarEstado(Long id, String status, String accessToken) {
        return this.restClient.put()
                .uri("/api/orders/{id}/status", id)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(Map.of("status", status))
                .retrieve()
                .body(Object.class);
    }

}