package com.learn.storageservice.dto;

import com.learn.storageservice.entity.StorageType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StorageDTO {
    private Long id;
    private StorageType storageType;
    private String bucket;
    private String path;
}

