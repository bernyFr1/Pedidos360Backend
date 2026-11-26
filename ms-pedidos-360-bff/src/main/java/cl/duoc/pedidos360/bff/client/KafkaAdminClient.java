package cl.duoc.pedidos360.bff.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KafkaAdminClient {

    private final RestClient restClient;

    public KafkaAdminClient(@Value("${services.admin-mq.url}") String adminMqUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(adminMqUrl)
                .build();
    }

    public Object cluster(String accessToken) {
        return restClient.get()
                .uri("/api/admin/kafka/cluster")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object topics(String accessToken) {
        return restClient.get()
                .uri("/api/admin/kafka/topics")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object topic(String name, String accessToken) {
        return restClient.get()
                .uri("/api/admin/kafka/topics/{name}", name)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public void crear(Object request, String accessToken) {
        restClient.post()
                .uri("/api/admin/kafka/topics")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void actualizarConfiguracion(String name, Object request, String accessToken) {
        restClient.put()
                .uri("/api/admin/kafka/topics/{name}/configs", name)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void eliminar(String name, String accessToken) {
        restClient.delete()
                .uri("/api/admin/kafka/topics/{name}", name)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    public Object groups(String accessToken) {
        return restClient.get()
                .uri("/api/admin/kafka/consumer-groups")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object group(String groupId, String accessToken) {
        return restClient.get()
                .uri("/api/admin/kafka/consumer-groups/{groupId}", groupId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }
}
