package com.learn.resource_service.service.impl;

import com.learn.resource_service.client.SongServiceClient;
import com.learn.resource_service.client.StorageServiceClient;
import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageType;
import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.repository.ResourceRepository;
import com.learn.resource_service.kafka.ResourceProducer;
import com.learn.resource_service.service.ResourceService;
import com.learn.resource_service.client.S3Service;
import jakarta.transaction.Transactional;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final S3Service s3Service;
    private final SongServiceClient songServiceClient;
    private final StorageServiceClient storageServiceClient;
    private final ResourceProducer resourceProducer;

    public ResourceServiceImpl(ResourceRepository resourceRepository,
                               S3Service s3Service,
                               SongServiceClient songServiceClient,
                               StorageServiceClient storageServiceClient,
                               ResourceProducer resourceProducer) {
        this.resourceRepository = resourceRepository;
        this.s3Service = s3Service;
        this.songServiceClient = songServiceClient;
        this.storageServiceClient = storageServiceClient;
        this.resourceProducer = resourceProducer;
    }

    @Override
    @Transactional(rollbackOn = Exception.class)
    public Long uploadResource(byte[] mp3Data) {
        log.info("Uploading new resource");
        validateMp3Data(mp3Data);

        String fileName = null;
        String s3Url;
        Resource resource = null;

        StorageDTO storage = storageServiceClient.getStorageByType(StorageType.STAGING);
        try {
            fileName = generateUniqueFileName();
            log.debug("Generated unique file name: {}", fileName);
            s3Url = s3Service.uploadMp3(mp3Data, fileName, storage);
            log.info("Uploaded file to S3: {}", s3Url);

            resource = new Resource();
            resource.setS3Url(s3Url);
            resource.setStorageType(StorageType.STAGING);
            resource = resourceRepository.save(resource);
            log.info("Saved resource to DB with ID: {}", resource.getId());

            if (resource.getId() == null) {
                throw new RuntimeException("Failed to save resource to database - ID is null");
            }

            if (!s3Service.fileExists(fileName, storage)) {
                throw new RuntimeException("S3 file verification failed after database save");
            }
            resourceProducer.sendId(resource.getId());
            log.info("Sent resource ID {} to Kafka topic", resource.getId());
            return resource.getId();
        } catch (Exception e) {
            log.error("Failed to upload resource, performing cleanup", e);
            performCleanupOnFailure(fileName, resource, storage);
            throw new RuntimeException("Failed to upload resource to S3", e);
        }
    }

    private void performCleanupOnFailure(String fileName, Resource resource, StorageDTO storage) {
        if (fileName != null) {
            log.warn("Cleaning up failed S3 upload for file: {}", fileName);
            s3Service.cleanupFailedUpload(fileName, storage);
        }

        if (resource != null && resource.getId() != null) {
            try {
                log.warn("Deleting resource from DB with ID: {}", resource.getId());
                resourceRepository.deleteById(resource.getId());
            } catch (Exception ex) {
                log.error("Failed to delete resource from database during cleanup: {}", ex.getMessage());
            }
        }
    }

    private String generateUniqueFileName() {
        return String.format("mp3_%d_%s.mp3",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public Resource getResourceById(Long id) {
        log.info("Fetching resource by ID: {}", id);
        validateId(id);
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Resource with ID=" + id + " not found"));
        StorageDTO storage = storageServiceClient.getStorageByType(resource.getStorageType());

        if (resource.getS3Url() != null) {
            String fileName = extractFileNameFromS3Url(resource.getS3Url());
            if (!s3Service.fileExists(fileName, storage)) {
                log.error("S3 file for resource ID={} does not exist", id);
                throw new NoSuchElementException("S3 file for resource ID=" + id + " does not exist");
            }
        }

        return resource;
    }

    private String extractFileNameFromS3Url(String s3Url) {
        // Format: https://bucket-name.s3.amazonaws.com/filename
        return s3Url.substring(s3Url.lastIndexOf("/") + 1);
    }

    @Override
    @Transactional(rollbackOn = Exception.class)
    public List<Long> deleteResourcesByIds(String csvIds) {
        log.info("Deleting resources by IDs: {}", csvIds);
        validateCsvIds(csvIds);
        List<Long> deletedIds = new ArrayList<>();

        List<Long> ids = Arrays.stream(csvIds.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .toList();

        ids.forEach(id -> {
            Optional<Resource> resourceOpt = resourceRepository.findById(id);
            if (resourceOpt.isPresent()) {
                Resource resource = resourceOpt.get();
                StorageDTO storage = storageServiceClient.getStorageByType(resource.getStorageType());

                try {
                    if (resource.getS3Url() != null) {
                        String fileName = extractFileNameFromS3Url(resource.getS3Url());
                        log.debug("Deleting file from S3: {}", fileName);
                        s3Service.deleteFile(fileName, storage);

                        if (s3Service.fileExists(fileName, storage)) {
                            log.error("S3 file deletion verification failed for: {}", fileName);
                            throw new RuntimeException("S3 file deletion verification failed for: " + fileName);
                        }
                    }

                    resourceRepository.delete(resource);
                    songServiceClient.deleteSongById(resource.getId());
                    deletedIds.add(id);
                    log.info("Deleted resource and song metadata for ID: {}", id);
                } catch (Exception ex) {
                    log.error("Failed to delete resource {} : {}", id, ex.getMessage());
                }
            }
        });

        return deletedIds;
    }

    @Override
    public byte[] getResourceContent(Long id) {
        log.info("Getting resource content for ID: {}", id);
        validateId(id);
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Resource with ID=" + id + " not found"));
        StorageDTO storage = storageServiceClient.getStorageByType(resource.getStorageType());

        if (resource.getS3Url() == null) {
            log.error("Resource {} has no S3 URL - cannot retrieve content", id);
            throw new RuntimeException("Resource " + id + " has no S3 URL - cannot retrieve content");
        }

        try {
            String fileName = extractFileNameFromS3Url(resource.getS3Url());
            byte[] content = s3Service.downloadFile(fileName, storage);
            log.info("Downloaded file from S3: {} ({} bytes)", fileName, content != null ? content.length : 0);
            validateMp3Data(content);
            return content;
        } catch (Exception e) {
            log.error("Failed to retrieve resource content for ID={}", id, e);
            throw new RuntimeException("Failed to retrieve resource content for ID=" + id, e);
        }
    }

    @Override
    public void process(Long resourceId) {
        log.info("Processing resource for permanent storage, ID: {}", resourceId);
        validateId(resourceId);
        StorageDTO permanentStorage = storageServiceClient.getStorageByType(StorageType.PERMANENT);
        StorageDTO stagingStorage = storageServiceClient.getStorageByType(StorageType.STAGING);
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource with ID=" + resourceId + " not found"));
        if (resource.getStorageType() == StorageType.PERMANENT) {
            log.info("Resource ID={} is already in permanent storage", resourceId);
            return;
        }
        if (resource.getS3Url() != null) {
            String fileName = extractFileNameFromS3Url(resource.getS3Url());
            try {
                log.info("Moving file {} from STAGING to PERMANENT storage", fileName);
                s3Service.moveFile(fileName, stagingStorage, permanentStorage);
                String newS3Url = s3Service.generatePresignedUrl(fileName, permanentStorage);
                resource.setS3Url(newS3Url);
                resource.setStorageType(StorageType.PERMANENT);
                resourceRepository.save(resource);
                log.info("Resource ID={} moved to permanent storage", resourceId);
            } catch (Exception e) {
                log.error("Failed to move resource ID={} to permanent storage", resourceId, e);
                throw new RuntimeException("Failed to move resource ID=" + resourceId + " to permanent storage", e);
            }
        }
    }

    private void validateMp3Data(byte[] mp3Data) {
        if (mp3Data == null || mp3Data.length == 0) {
            throw new IllegalArgumentException("Audio file is required");
        }

        Tika tika = new Tika();
        String mimeType = tika.detect(mp3Data);
        if (!"audio/mpeg".equals(mimeType)) {
            throw new IllegalArgumentException("Invalid MP3 file");
        }
    }

    public void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid ID = " + id);
        }
    }

    private void validateCsvIds(String csvIds) {
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
