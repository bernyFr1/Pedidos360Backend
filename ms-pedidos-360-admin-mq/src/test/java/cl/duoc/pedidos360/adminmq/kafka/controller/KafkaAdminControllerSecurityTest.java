package cl.duoc.pedidos360.adminmq.kafka.controller;

import cl.duoc.pedidos360.adminmq.config.SecurityConfig;
import cl.duoc.pedidos360.adminmq.controller.AdminExceptionHandler;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicResponse;
import cl.duoc.pedidos360.adminmq.kafka.service.KafkaAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KafkaAdminController.class)
@Import({SecurityConfig.class, AdminExceptionHandler.class})
class KafkaAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KafkaAdminService kafkaAdminService;

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
        when(kafkaAdminService.listarTopicos()).thenReturn(List.of(
                new KafkaTopicResponse("pedidos-eventos", false, 3, 3, Map.of(), List.of())));

        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("pedidos-eventos"))
                .andExpect(jsonPath("$[0].partitions").value(3));
    }

    @Test
    void crearTopicoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/kafka/topics")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"partitions\":0,\"replicationFactor\":0}"))
                .andExpect(status().isBadRequest());
    }
}
