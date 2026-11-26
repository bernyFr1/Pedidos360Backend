package cl.duoc.pedidos360.adminmq.kafka.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    AdminClient kafkaAdminClient(
            @Value("${app.kafka.bootstrap-servers:localhost:9092,localhost:9093,localhost:9094}") String bootstrapServers) {
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000));
    }
}
