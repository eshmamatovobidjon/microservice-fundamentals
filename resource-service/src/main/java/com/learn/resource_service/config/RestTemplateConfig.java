package com.learn.resource_service.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    private final RestTemplateInterceptor restTemplateInterceptor;

    private final JwtTokenRelayInterceptor jwtTokenRelayInterceptor;

    public RestTemplateConfig(RestTemplateInterceptor restTemplateInterceptor, JwtTokenRelayInterceptor jwtTokenRelayInterceptor) {
        this.restTemplateInterceptor = restTemplateInterceptor;
        this.jwtTokenRelayInterceptor = jwtTokenRelayInterceptor;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .interceptors(restTemplateInterceptor, jwtTokenRelayInterceptor)
                .build();
    }
}