package cl.duoc.pedidos360.notify;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class MsPedidos360NotifyApplicationTests {

	@MockitoBean
	private RabbitAdmin rabbitAdmin;

	@Test
	void contextLoads() {
	}

}
