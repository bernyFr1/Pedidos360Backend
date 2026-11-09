package cl.duoc.pedidos360.adminmq.monitoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia la API de RabbitMQ Management.
 * La URL y las credenciales se configuran en application.properties o por variables de entorno.
 *
 * Se usa SimpleClientHttpRequestFactory porque RabbitMQ Management (Cowboy) cierra la
 * conexión cuando el cliente JDK envía el body con Transfer-Encoding chunked.
 */
@Configuration
public class RabbitMqManagementConfig {

    @Bean
    RestClient rabbitMqManagementRestClient(
            @Value("${app.rabbitmq.management.url:http://localhost:15672/api}") String baseUrl,
            @Value("${app.rabbitmq.management.username:pedidos360}") String username,
            @Value("${app.rabbitmq.management.password:pedidos360}") String password) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new BasicAuthenticationInterceptor(username, password))
                .build();
    }
}
