package com.learn.storageservice.service;

import com.learn.storageservice.dto.StorageDTO;
import com.learn.storageservice.entity.Storage;
import com.learn.storageservice.entity.StorageType;

import java.util.List;

public interface StorageService {
    Storage createStorage(StorageDTO storageDTO);

    Storage getStorage(Long id);

    List<Long> deleteStorages(String csvIds);

    Storage getStorageByType(StorageType storageType);
}
