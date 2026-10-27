package cl.duoc.pedidos360.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.rabbit")
public record RabbitProperties(
        List<String> dominios,
        Exchange exchange,
        Map<String, String> queues,
        Map<String, String> routingKeys,
        Map<String, String> topicPatterns) {

    public record Exchange(String direct, String topic, String dlx) {
    }

    public String mainQueue(String dominio) {
        return queues.get(dominio);
    }

    public String dlq(String dominio) {
        return queues.get(dominio + "-dlq");
    }

    public String routingKey(String dominio) {
        return routingKeys.get(dominio);
    }

    public String topicPattern(String dominio) {
        return topicPatterns.get(dominio);
    }
}