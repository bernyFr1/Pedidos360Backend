package cl.duoc.pedidos360.adminmq.controller;

import cl.duoc.pedidos360.adminmq.config.SecurityConfig;
import cl.duoc.pedidos360.adminmq.dto.QueueResponse;
import cl.duoc.pedidos360.adminmq.service.RabbitAdminService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RabbitAdminController.class)
@Import({SecurityConfig.class, AdminExceptionHandler.class, AdminJwtIntegrationTest.JwtTestConfig.class})
class AdminJwtIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private RabbitAdminService rabbitAdminService;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/queues/q.test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFirmadoSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/queues/q.test")
                        .header("Authorization", "Bearer " + token("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenFirmadoConRolAdminObtieneCola() throws Exception {
        when(rabbitAdminService.obtenerCola("q.test"))
                .thenReturn(new QueueResponse("q.test", 0, 0));

        mockMvc.perform(get("/api/admin/queues/q.test")
                        .header("Authorization", "Bearer " + token("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("q.test"));
    }

    @Test
    void tokenFirmadoConRolAdminCreaCola() throws Exception {
        when(rabbitAdminService.crearCola(any()))
                .thenReturn(new QueueResponse("q.test", 0, 0));

        mockMvc.perform(post("/api/admin/queues")
                        .header("Authorization", "Bearer " + token("ADMINISTRADOR"))
                        .contentType("application/json")
                        .content("{\"name\":\"q.test\",\"durable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("q.test"));
    }

    private String token(String rol) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://test-issuer")
                .subject("admin-test")
                .audience(List.of("test-audience"))
                .claim("roles", List.of(rol))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @TestConfiguration
    static class JwtTestConfig {

        private static final KeyPair KEY_PAIR = generarKeyPair();
        private static final RSAKey RSA_KEY = new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
                .privateKey((RSAPrivateKey) KEY_PAIR.getPrivate())
                .keyID("test-key")
                .build();

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
        }

        @Bean
        JwtEncoder jwtEncoder() {
            JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(RSA_KEY));
            return new NimbusJwtEncoder(jwkSource);
        }

        private static KeyPair generarKeyPair() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("No se pudo generar el par de claves RSA", exception);
            }
        }
    }
}
