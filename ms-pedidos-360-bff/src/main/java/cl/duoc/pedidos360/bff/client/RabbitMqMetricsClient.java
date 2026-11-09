package cl.duoc.pedidos360.bff.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente del BFF hacia los endpoints de monitoreo del microservicio admin-mq.
 */
@Component
public class RabbitMqMetricsClient {

    private final RestClient restClient;

    public RabbitMqMetricsClient(@Value("${services.admin-mq.url}") String adminMqUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(adminMqUrl)
                .build();
    }

    public Object overview(String accessToken) {
        return restClient.get()
                .uri("/api/admin/metrics/rabbitmq/overview")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object queues(String accessToken) {
        return restClient.get()
                .uri("/api/admin/metrics/rabbitmq/queues")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object dlqMessages(String queue, Integer limit, String accessToken) {
        return restClient.get()
                .uri(builder -> {
                    builder.path("/api/admin/metrics/rabbitmq/dlq/messages");
                    if (queue != null && !queue.isBlank()) {
                        builder.queryParam("queue", queue);
                    }
                    if (limit != null) {
                        builder.queryParam("limit", limit);
                    }
                    return builder.build();
                })
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Object.class);
    }

    public Object requeue(Object request, String accessToken) {
        return restClient.post()
                .uri("/api/admin/metrics/rabbitmq/dlq/requeue")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(request)
                .retrieve()
                .body(Object.class);
    }
}
