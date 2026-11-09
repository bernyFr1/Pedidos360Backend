package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.RabbitMqMetricsClient;
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

@WebMvcTest(RabbitMqMetricsController.class)
@Import(SecurityConfig.class)
class RabbitMqMetricsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitMqMetricsClient metricsClient;

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
    void conRolAdminObtieneOverview() throws Exception {
        when(metricsClient.overview(anyString()))
                .thenReturn(Map.of("status", "UP"));

        mockMvc.perform(get("/api/admin/metrics/rabbitmq/overview")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void conRolAdminReencolaMensaje() throws Exception {
        when(metricsClient.requeue(any(), anyString()))
                .thenReturn(Map.of("requeued", true));

        mockMvc.perform(post("/api/admin/metrics/rabbitmq/dlq/requeue")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"q.cmd.email.dlq\",\"reference\":\"ref-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(true));
    }

    @Test
    void conRolAdminConsultaMensajesDlq() throws Exception {
        when(metricsClient.dlqMessages(eq("q.cmd.email.dlq"), eq(5), anyString()))
                .thenReturn(java.util.List.of(Map.of("reference", "ref-1")));

        mockMvc.perform(get("/api/admin/metrics/rabbitmq/dlq/messages")
                        .queryParam("queue", "q.cmd.email.dlq")
                        .queryParam("limit", "5")
                        .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("ref-1"));
    }
}
