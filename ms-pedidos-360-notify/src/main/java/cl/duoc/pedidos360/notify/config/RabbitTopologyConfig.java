package cl.duoc.pedidos360.notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitTopologyConfig {

    @Bean
    public DirectExchange cmdDirectExchange(RabbitProperties props) {
        return new DirectExchange(props.exchange().direct(), true, false);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public ApplicationRunner rabbitTopologyInitializer(RabbitAdmin rabbitAdmin) {
        return args -> rabbitAdmin.initialize();
    }

    @Bean
    public TopicExchange cmdTopicExchange(RabbitProperties props) {
        return new TopicExchange(props.exchange().topic(), true, false);
    }

    @Bean
    public DirectExchange cmdDeadLetterExchange(RabbitProperties props) {
        return new DirectExchange(props.exchange().dlx(), true, false);
    }

    @Bean
    public Declarables casoPedidos360Topology(
            RabbitProperties props,
            @Qualifier("cmdDirectExchange") DirectExchange direct,
            @Qualifier("cmdTopicExchange") TopicExchange topic,
            @Qualifier("cmdDeadLetterExchange") DirectExchange dlx) {

        List<Declarable> declarables = new ArrayList<>();

        for (String dominio : props.dominios()) {

            Queue mainQueue = QueueBuilder.durable(props.mainQueue(dominio))
                    .withArgument("x-dead-letter-exchange", props.exchange().dlx())
                    .withArgument("x-dead-letter-routing-key", props.routingKey(dominio))
                    .build();

            Queue dlq = QueueBuilder.durable(props.dlq(dominio)).build();

            declarables.add(mainQueue);
            declarables.add(dlq);

            // Comando exacto: cmd.direct / email.send
            declarables.add(BindingBuilder.bind(mainQueue)
                    .to(direct)
                    .with(props.routingKey(dominio)));

            // Variantes: cmd.topic / email.*
            declarables.add(BindingBuilder.bind(mainQueue)
                    .to(topic)
                    .with(props.topicPattern(dominio)));

            // Mensaje muerto: cmd.dead.dlx / email.send
            declarables.add(BindingBuilder.bind(dlq)
                    .to(dlx)
                    .with(props.routingKey(dominio)));
        }

        return new Declarables(declarables);
    }
}