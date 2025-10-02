package com.learn.songservice.config;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // Get trace ID from MDC
        String traceId = MDC.get(TRACE_ID_MDC_KEY);

        // Add trace ID to request headers
        if (traceId != null && !traceId.isEmpty()) {
            request.getHeaders().add(TRACE_ID_HEADER, traceId);
        }

        // Continue with the request
        return execution.execute(request, body);
    }
}