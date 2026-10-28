package cl.duoc.pedidos360.adminmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MsPedidos360AdminMqApplicationTests {

    @MockitoBean
    private RabbitAdmin rabbitAdmin;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }
}
