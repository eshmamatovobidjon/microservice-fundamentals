package com.learn.resource_processor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.io.IOException;

@Slf4j
public class OAuth2ClientCredentialsRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager manager;

    public OAuth2ClientCredentialsRestTemplateInterceptor(OAuth2AuthorizedClientManager manager) {
        this.manager = manager;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        OAuth2AuthorizeRequest oAuth2AuthorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("keycloak")
                .principal("resource-processor-service")
                .build();

        OAuth2AuthorizedClient client = manager.authorize(oAuth2AuthorizeRequest);

        if (client == null) {
            log.error("Failed to authorize client");
            throw new IllegalStateException("Failed to authorize client 'keycloak'");
        }

        String token = client.getAccessToken().getTokenValue();
        log.debug("Adding OAuth2 token to request");
        request.getHeaders().add("Authorization", "Bearer " + token);

        return execution.execute(request, body);
    }
}