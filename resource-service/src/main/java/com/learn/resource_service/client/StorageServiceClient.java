package com.learn.resource_service.client;

import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class StorageServiceClient {
    private final RestTemplate restTemplate;
    private final String storageServiceUrl;

    public StorageServiceClient(RestTemplate restTemplate,
                                @Value("${storage-service.url}") String url,
                                @Value("${storage-service.port}") String port) {
        this.restTemplate = restTemplate;
        this.storageServiceUrl = "http://" + url + ":" + port + "/storages";
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public StorageDTO getStorageByType(StorageType type) {
        return restTemplate.getForObject(storageServiceUrl + "?type=" + type, StorageDTO.class);
    }
}
