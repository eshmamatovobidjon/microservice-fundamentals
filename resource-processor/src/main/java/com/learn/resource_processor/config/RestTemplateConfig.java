package com.learn.resource_processor.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestTemplate;


@Configuration
public class RestTemplateConfig {

    private final RestTemplateInterceptor restTemplateInterceptor;

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public RestTemplateConfig(RestTemplateInterceptor restTemplateInterceptor, OAuth2AuthorizedClientManager authorizedClientManager) {
        this.restTemplateInterceptor = restTemplateInterceptor;
        this.authorizedClientManager = authorizedClientManager;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .interceptors(restTemplateInterceptor, new OAuth2ClientCredentialsRestTemplateInterceptor(authorizedClientManager))
                .build();
    }
}