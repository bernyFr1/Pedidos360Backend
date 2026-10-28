package cl.duoc.pedidos360.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitPublisherConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitPublisherConfig.class);

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);
            template.setReturnsCallback(returned -> log.warn(
                    "Mensaje no enrutable: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText()
            ));
        };
    }
}
