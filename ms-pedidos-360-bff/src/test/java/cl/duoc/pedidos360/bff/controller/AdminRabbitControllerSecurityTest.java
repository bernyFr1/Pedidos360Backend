package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.RabbitAdminClient;
import cl.duoc.pedidos360.bff.config.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRabbitController.class)
@Import(SecurityConfig.class)
class AdminRabbitControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitAdminClient rabbitAdminClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/queues/q.test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/queues/q.test").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void conRolAdminObtieneCola() throws Exception {
        when(rabbitAdminClient.obtenerCola(eq("q.test"), anyString()))
                .thenReturn(Map.of("name", "q.test"));

        mockMvc.perform(get("/api/admin/rabbit/queues/q.test")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("q.test"));
    }

    @Test
    void conRolAdminCreaCola() throws Exception {
        when(rabbitAdminClient.crearCola(any(), anyString()))
                .thenReturn(Map.of("name", "q.test"));

        mockMvc.perform(post("/api/admin/rabbit/queues")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.test\",\"durable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("q.test"));
    }
}
