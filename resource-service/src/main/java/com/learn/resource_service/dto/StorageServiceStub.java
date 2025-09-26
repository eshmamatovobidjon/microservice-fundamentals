package com.learn.resource_service.dto;

import org.springframework.stereotype.Component;

@Component
public class StorageServiceStub {
    public StorageDTO getDefaultStorage(StorageType type) {
        return StorageDTO.builder()
                .storageType(type)
                .bucket("my-app-mp3-resources")
                .path(type == StorageType.PERMANENT ? "/fallback-permanent" : "/fallback-staging")
                .build();
    }
}
