package com.learn.apigateway.controller;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayErrorController {

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", OffsetDateTime.now().toString());
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        m.put("message", message);
        return m;
    }

    @RequestMapping("/fallback/songs")
    public ResponseEntity<Map<String, Object>> songServiceFallback() {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(body(status, "Song service is currently unavailable. Please try again later."));
    }

    @RequestMapping("/fallback/resources")
    public ResponseEntity<Map<String, Object>> resourceServiceFallback() {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(body(status, "Resource service is currently unavailable. Please try again later."));
    }

    @RequestMapping("/fallback/metadata")
    public ResponseEntity<Map<String, Object>> resourceProcessorFallback() {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(body(status, "Resource processor is currently unavailable. Please try again later."));
    }

    @RequestMapping("/fallback/storages")
    public ResponseEntity<Map<String, Object>> storageServiceFallback() {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(body(status, "Storage service is currently unavailable. Please try again later."));
    }

    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> routeNotFound() {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .body(body(status, "The requested endpoint does not exist."));
    }
}
