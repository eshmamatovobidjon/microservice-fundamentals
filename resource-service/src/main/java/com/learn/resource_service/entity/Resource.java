package com.learn.resource_service.entity;

import com.learn.resource_service.dto.StorageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "resources")
public class Resource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String s3Url;
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private StorageType storageType;

    private LocalDateTime uploadedAt;

    public Resource() {
        this.uploadedAt = LocalDateTime.now();
    }

    public Resource(String s3Url) {
        this();
        this.s3Url = s3Url;
    }
}
