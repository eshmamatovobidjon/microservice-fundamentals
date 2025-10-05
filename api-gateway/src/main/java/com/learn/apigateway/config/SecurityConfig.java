package com.learn.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers("/actuator/**", "/actuator/health/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()

                        // Resource endpoints
                        .pathMatchers(HttpMethod.POST, "/resources/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/resources/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/resources/**").hasAnyRole("ADMIN", "USER")

                        // Song endpoints
                        .pathMatchers(HttpMethod.POST, "/songs/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/songs/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/songs/**").hasAnyRole("ADMIN", "USER")

                        // Storage endpoints
                        .pathMatchers(HttpMethod.POST, "/storages/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/storages/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/storages/**").hasAnyRole("ADMIN", "USER")

                        // All other requests require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new ReactiveKeycloakRoleConverter());
        return converter;
    }
}