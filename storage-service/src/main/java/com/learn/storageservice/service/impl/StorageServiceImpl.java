package com.learn.storageservice.service.impl;

import com.learn.storageservice.dto.StorageDTO;
import com.learn.storageservice.entity.Storage;
import com.learn.storageservice.entity.StorageType;
import com.learn.storageservice.exception.ConflictException;
import com.learn.storageservice.repository.StorageRepository;
import com.learn.storageservice.service.StorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class StorageServiceImpl implements StorageService {
    private final StorageRepository storageRepository;

    public StorageServiceImpl(StorageRepository storageRepository) {
        this.storageRepository = storageRepository;
    }

    @Override
    public Storage createStorage(StorageDTO storageDTO) {
        Storage storage = convertToEntity(storageDTO);
        if (storageRepository.existsByStorageTypeAndBucketAndPath(storage.getStorageType(),
                storage.getBucket(),
                storage.getPath())) {
            throw new ConflictException("Storage metadata already exists with these parameters.");
        }
        System.out.println("Storage to be created: " + storage);
        return storageRepository.save(storage);
    }

    private Storage convertToEntity(StorageDTO storageDTO) {
        Storage storage = new Storage();
        storage.setId(storageDTO.getId());
        storage.setStorageType(storageDTO.getStorageType());
        storage.setBucket(storageDTO.getBucket());
        storage.setPath(storageDTO.getPath());
        return storage;
    }

    @Override
    public Storage getStorage(Long id) {
        validateId(id);
        return storageRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Storage with ID=" + id + " not found"));
    }

    @Override
    public List<Long> deleteStorages(String csvIds) {
        validateCsvIds(csvIds);
        String[] idArray = csvIds.split(",");
        List<Long> deletedIds = new ArrayList<>();
        for (String idStr : idArray) {
            try {
                Long id = Long.valueOf(idStr.trim());
                if (storageRepository.existsById(id)) {
                    storageRepository.deleteById(id);
                    deletedIds.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return deletedIds;
    }

    @Override
    public Storage getStorageByType(StorageType storageType) {
        if (storageType == null) {
            throw new IllegalArgumentException("Storage type is required");
        }
        return storageRepository.getByStorageType(storageType)
                .orElseThrow(() -> new NoSuchElementException("Storage with type=" + storageType + " not found"));
    }

    public void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid ID");
        }
    }

    public void validateCsvIds(String csvIds) {
        if (csvIds == null || csvIds.isEmpty()) {
            throw new IllegalArgumentException("CSV IDs are required");
        }
        if (csvIds.length() >= 200) {
            throw new IllegalArgumentException("CSV string length must be less than 200 characters. Got " + csvIds.length());
        }
        String[] ids = csvIds.split(",");
        for (String idStr : ids) {
            try {
                long id = Long.parseLong(idStr.trim());
                validateId(id);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid ID format: " + idStr);
            }
        }
    }
}
