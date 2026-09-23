package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.config.SecurityConfig;
import cl.duoc.pedidos360.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void tokenAusenteDevuelve401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoSinPermisosDevuelve403() throws Exception {
        mockMvc.perform(get("/api/orders").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenInvalidoDevuelve401() throws Exception {
        when(jwtDecoder.decode("invalid"))
                .thenThrow(new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "JWT inválido"));

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoConRolDevuelve200() throws Exception {
        mockMvc.perform(get("/api/orders")
                .with(jwt().authorities(() -> "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk());
    }
}