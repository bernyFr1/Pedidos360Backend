package cl.duoc.pedidos360.adminmq.controller;

import cl.duoc.pedidos360.adminmq.config.SecurityConfig;
import cl.duoc.pedidos360.adminmq.dto.QueueResponse;
import cl.duoc.pedidos360.adminmq.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RabbitAdminController.class)
@Import({SecurityConfig.class, AdminExceptionHandler.class})
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitAdminService rabbitAdminService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/queues/q.test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinRolDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/queues/q.test").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenConRolDevuelve200() throws Exception {
        when(rabbitAdminService.obtenerCola("q.test"))
                .thenReturn(new QueueResponse("q.test", 0, 0));

        mockMvc.perform(get("/api/admin/queues/q.test")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("q.test"));
    }

    @Test
    void crearColaConRolDevuelve201() throws Exception {
        when(rabbitAdminService.crearCola(any()))
                .thenReturn(new QueueResponse("q.test", 0, 0));

        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.test\",\"durable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("q.test"));
    }

    @Test
    void crearColaInvalidaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
