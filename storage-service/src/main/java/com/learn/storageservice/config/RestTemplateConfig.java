package com.learn.storageservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Configuration
public class RestTemplateConfig {

    private final RestTemplateInterceptor restTemplateInterceptor;

    public RestTemplateConfig(RestTemplateInterceptor restTemplateInterceptor) {
        this.restTemplateInterceptor = restTemplateInterceptor;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .interceptors(Collections.singletonList(restTemplateInterceptor))
                .build();
    }
}