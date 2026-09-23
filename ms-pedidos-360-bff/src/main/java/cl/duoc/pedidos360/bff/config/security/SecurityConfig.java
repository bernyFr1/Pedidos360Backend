package cl.duoc.pedidos360.bff.config.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	http
		.cors(Customizer.withDefaults())
		.csrf(csrf -> csrf.disable())
		.sessionManagement(session -> session
			.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.authorizeHttpRequests(auth -> auth
			.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
			.requestMatchers("/api/publico").permitAll()
			.requestMatchers(HttpMethod.GET, "/api/pedidos/**")
			.hasAuthority("SCOPE_Pedidos.Read")
			.anyRequest()
			.authenticated())
		.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

	return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
	CorsConfiguration config = new CorsConfiguration();
	config.setAllowedOriginPatterns(List.of(
		"http://localhost:4200",
		"https://*.amazonaws.com"));
	config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
	config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

	UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
	source.registerCorsConfiguration("/**", config);
	return source;
    }
}
