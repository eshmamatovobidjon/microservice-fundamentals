package com.learn.resource_service.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StorageDTO {
    private Long id;
    private StorageType storageType;
    private String bucket;
    private String path;
}

