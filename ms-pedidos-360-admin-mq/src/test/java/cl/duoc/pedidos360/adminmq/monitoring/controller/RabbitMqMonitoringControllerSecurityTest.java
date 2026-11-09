package cl.duoc.pedidos360.adminmq.monitoring.controller;

import cl.duoc.pedidos360.adminmq.config.SecurityConfig;
import cl.duoc.pedidos360.adminmq.controller.AdminExceptionHandler;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RabbitMqOverviewResponse;
import cl.duoc.pedidos360.adminmq.monitoring.service.RabbitMqMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RabbitMqMonitoringController.class)
@Import({SecurityConfig.class, AdminExceptionHandler.class})
class RabbitMqMonitoringControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitMqMonitoringService monitoringService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/metrics/rabbitmq/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinRolDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/metrics/rabbitmq/overview").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenAdminObtieneOverview() throws Exception {
        when(monitoringService.obtenerOverview())
                .thenReturn(RabbitMqOverviewResponse.down("sin broker"));

        mockMvc.perform(get("/api/admin/metrics/rabbitmq/overview")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void tokenConRolAdminTambienPermiteAcceso() throws Exception {
        when(monitoringService.obtenerOverview())
                .thenReturn(RabbitMqOverviewResponse.down("sin broker"));

        mockMvc.perform(get("/api/admin/metrics/rabbitmq/overview")
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void requeueSinDatosDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/metrics/rabbitmq/dlq/requeue")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
