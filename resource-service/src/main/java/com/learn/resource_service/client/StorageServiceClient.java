package com.learn.resource_service.client;

import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageServiceStub;
import com.learn.resource_service.dto.StorageType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class StorageServiceClient {
    private final RestTemplate restTemplate;
    private final String storageServiceUrl;
    private final StorageServiceStub stub;
    private final CircuitBreaker circuitBreaker;

    public StorageServiceClient(RestTemplate restTemplate,
                                @Value("${storage-service.url}") String url,
                                @Value("${storage-service.port}") String port,
                                StorageServiceStub stub,
                                CircuitBreaker circuitBreaker) {
        this.restTemplate = restTemplate;
        this.stub = stub;
        this.circuitBreaker = circuitBreaker;
        this.storageServiceUrl = "http://" + url + ":" + port + "/storages";
    }

    public StorageDTO getStorageByType(StorageType type) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                return restTemplate.getForObject(storageServiceUrl + "?type=" + type, StorageDTO.class);
            } catch (Exception e) {
                return stub.getDefaultStorage(type);
            }
        });
    }
}
