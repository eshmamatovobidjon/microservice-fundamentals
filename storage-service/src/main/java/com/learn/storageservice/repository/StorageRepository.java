package com.learn.storageservice.repository;

import com.learn.storageservice.entity.Storage;
import com.learn.storageservice.entity.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageRepository extends JpaRepository<Storage, Long> {
    boolean existsByStorageTypeAndBucketAndPath(StorageType type, String bucket, String path);
    Optional<Storage> getByStorageType(StorageType storageType);
}
