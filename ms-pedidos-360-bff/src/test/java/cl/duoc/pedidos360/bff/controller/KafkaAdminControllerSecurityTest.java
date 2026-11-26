package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.KafkaAdminClient;
import cl.duoc.pedidos360.bff.config.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KafkaAdminController.class)
@Import(SecurityConfig.class)
class KafkaAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KafkaAdminClient kafkaAdminClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinRolDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenAdminListaTopicos() throws Exception {
        when(kafkaAdminClient.topics(anyString()))
                .thenReturn(List.of(Map.of("name", "pedidos-eventos", "partitions", 3)));

        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("pedidos-eventos"));
    }
}
