package cl.duoc.pedidos360.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class MsPedidos360AuditApplicationTests {

	@Test
	void contextLoads() {
	}

}
