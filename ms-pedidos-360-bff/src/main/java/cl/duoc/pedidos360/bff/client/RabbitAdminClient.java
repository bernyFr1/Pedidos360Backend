package cl.duoc.pedidos360.bff.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RabbitAdminClient {

    private final RestClient restClient;

    public RabbitAdminClient(@Value("${services.admin-mq.url}") String adminMqUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(adminMqUrl)
                .build();
    }

    public Object crearCola(Object request, String accessToken) {
        return this.restClient.post()
                .uri("/api/admin/queues")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .body(Object.class);
    }

    public Object obtenerCola(String name, String accessToken) {
        return this.restClient.get()
                .uri("/api/admin/queues/{name}", name)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public void eliminarCola(String name, String accessToken) {
        this.restClient.delete()
                .uri("/api/admin/queues/{name}", name)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    public void crearExchange(Object request, String accessToken) {
        this.restClient.post()
                .uri("/api/admin/exchanges")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void eliminarExchange(String name, String accessToken) {
        this.restClient.delete()
                .uri("/api/admin/exchanges/{name}", name)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    public void crearBinding(Object request, String accessToken) {
        this.restClient.post()
                .uri("/api/admin/bindings")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void eliminarBinding(String queue, String exchange, String routingKey, String accessToken) {
        this.restClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/api/admin/bindings")
                        .queryParam("queue", queue)
                        .queryParam("exchange", exchange)
                        .queryParam("routingKey", routingKey)
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }
}
