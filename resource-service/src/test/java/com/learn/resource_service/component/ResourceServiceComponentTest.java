package com.learn.resource_service.component;

import com.learn.resource_service.client.S3Service;
import com.learn.resource_service.client.SongServiceClient;
import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageType;
import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.kafka.ResourceProducer;
import com.learn.resource_service.repository.ResourceRepository;
import com.learn.resource_service.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = "resource-created")
@Testcontainers
class ResourceServiceComponentTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("resource_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceRepository resourceRepository;

    @MockitoBean // Mock external S3 service
    private S3Service s3Service;

    @MockitoBean
    private SongServiceClient songServiceClient;

    @Autowired
    private ResourceProducer resourceProducer; // Real Kafka producer

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private byte[] validMp3Data;

    private StorageDTO stagingStorage;

    @BeforeEach
    void setUp() {
        resourceRepository.deleteAll();
        validMp3Data = createValidMp3Data();

        stagingStorage = new StorageDTO();
        stagingStorage.setId(1L);
        stagingStorage.setStorageType(StorageType.STAGING);
        stagingStorage.setBucket("test-bucket");
        stagingStorage.setPath("staging/");
    }

    private byte[] createValidMp3Data() {
        return new byte[]{
                (byte)0xFF, (byte)0xFB, 0x9, 0x00,
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05
        };
    }

    @Test
    @DisplayName("Component Test: Should upload resource with real DB and Kafka, mocked S3")
    void uploadResource_WithRealDBAndKafka_Success() {
        // Given
        String s3Url = "https://test-bucket.s3.amazonaws.com/test-file.mp3";
        when(s3Service.uploadMp3(any(byte[].class), anyString(), any())).thenReturn(s3Url);
        when(s3Service.fileExists(anyString(), any())).thenReturn(true);

        // When
        Long resourceId = resourceService.uploadResource(validMp3Data);

        // Then
        assertNotNull(resourceId);

        // Verify real database interaction
        Optional<Resource> savedResource = resourceRepository.findById(resourceId);
        assertTrue(savedResource.isPresent());
        assertEquals(s3Url, savedResource.get().getS3Url());
        assertNotNull(savedResource.get().getUploadedAt());

        // Verify mocked S3 interactions
        verify(s3Service).fileExists(anyString(), any());
    }

    @Test
    @DisplayName("Component Test: Should retrieve resource with real DB query and S3 verification")
    void getResourceById_WithRealDB_Success() {
        // Given - Create resource directly in real database
        Resource resource = new Resource();
        resource.setS3Url("https://test-bucket.s3.amazonaws.com/existing-file.mp3");
        Resource savedResource = resourceRepository.save(resource);

        when(s3Service.fileExists("existing-file.mp3", stagingStorage)).thenReturn(true);

        // When
        Resource result = resourceService.getResourceById(savedResource.getId());

        // Then
        assertEquals(savedResource.getId(), result.getId());
        assertEquals(savedResource.getS3Url(), result.getS3Url());

        // Verify real database was queried
        verify(s3Service).fileExists("existing-file.mp3", stagingStorage);
    }

    @Test
    @DisplayName("Component Test: Should delete resources from real DB with S3 cleanup")
    void deleteResourcesByIds_WithRealDB_Success() {
        // Given - Create resources in real database
        Resource resource1 = resourceRepository.save(createResource("file1.mp3"));
        Resource resource2 = resourceRepository.save(createResource("file2.mp3"));
        Resource resource3 = resourceRepository.save(createResource("file3.mp3"));

        when(s3Service.fileExists(anyString(), any())).thenReturn(false); // Simulate successful S3 deletion
        doNothing().when(songServiceClient).deleteSongById(anyLong());
        String csvIds = String.format("%d,%d,%d", resource1.getId(), resource2.getId(), resource3.getId());

        // When
        List<Long> deletedIds = resourceService.deleteResourcesByIds(csvIds);

        // Then
        assertEquals(3, deletedIds.size());
        assertTrue(deletedIds.containsAll(Arrays.asList(resource1.getId(), resource2.getId(), resource3.getId())));

        // Verify real database deletions
        assertFalse(resourceRepository.existsById(resource1.getId()));
        assertFalse(resourceRepository.existsById(resource2.getId()));
        assertFalse(resourceRepository.existsById(resource3.getId()));

        // Verify S3 cleanup calls
        verify(s3Service).deleteFile("file1.mp3", stagingStorage);
        verify(s3Service).deleteFile("file2.mp3", stagingStorage);
        verify(s3Service).deleteFile("file3.mp3", stagingStorage);
    }

    @Test
    @DisplayName("Component Test: Should handle partial deletion with real DB transactions")
    void deleteResourcesByIds_PartialS3Failure_PartialDBDeletion() {
        // Given
        Resource resource1 = resourceRepository.save(createResource("file1.mp3"));
        Resource resource2 = resourceRepository.save(createResource("file2.mp3"));

        // S3 deletion fails for first file, succeeds for second
        doThrow(new RuntimeException("S3 delete failed")).when(s3Service).deleteFile("file1.mp3", stagingStorage);
        when(s3Service.fileExists("file1.mp3", stagingStorage)).thenReturn(true); // Still exists
        when(s3Service.fileExists("file2.mp3", stagingStorage)).thenReturn(false); // Deleted successfully
        doNothing().when(songServiceClient).deleteSongById(anyLong());
        String csvIds = String.format("%d,%d", resource1.getId(), resource2.getId());

        // When
        List<Long> deletedIds = resourceService.deleteResourcesByIds(csvIds);

        // Then
        assertEquals(1, deletedIds.size());
        assertEquals(resource2.getId(), deletedIds.get(0));

        // Verify partial deletion in real database
        assertTrue(resourceRepository.existsById(resource1.getId())); // Should still exist
        assertFalse(resourceRepository.existsById(resource2.getId())); // Should be deleted
    }

    @Test
    @DisplayName("Component Test: Should download content with real DB and mocked S3")
    void getResourceContent_WithRealDB_Success() {
        // Given
        Resource resource = resourceRepository.save(createResource("download-test.mp3"));
        when(s3Service.downloadFile("download-test.mp3",stagingStorage)).thenReturn(validMp3Data);

        // When
        byte[] content = resourceService.getResourceContent(resource.getId());

        // Then
        assertArrayEquals(validMp3Data, content);

        // Verify real DB query happened
        verify(s3Service).downloadFile("download-test.mp3", stagingStorage);
    }

    @Test
    @DisplayName("Component Test: Should validate MP3 data format correctly")
    void uploadResource_InvalidMp3Data_ValidationWorks() {
        // Given
        byte[] invalidData = "not mp3 data".getBytes();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resourceService.uploadResource(invalidData)
        );

        assertEquals("Invalid MP3 file", exception.getMessage());

        // Verify no external calls made due to early validation
        verifyNoInteractions(s3Service);
        assertEquals(0, resourceRepository.count());
    }

    @Test
    @DisplayName("Component Test: Should handle database transaction boundaries correctly")
    @Transactional
    void uploadResource_TransactionBoundary_WorksCorrectly() {
        // Given
        String s3Url = "https://test-bucket.s3.amazonaws.com/transaction-test.mp3";
        when(s3Service.uploadMp3(any(byte[].class), anyString(), any())).thenReturn(s3Url);
        when(s3Service.fileExists(anyString(), any())).thenReturn(true);

        // When
        Long resourceId = resourceService.uploadResource(validMp3Data);

        // Then - Within transaction, resource should exist
        assertTrue(resourceRepository.existsById(resourceId));

        // Verify the resource has all expected properties set
        Resource resource = resourceRepository.findById(resourceId).get();
        assertEquals(s3Url, resource.getS3Url());
        assertNotNull(resource.getUploadedAt());
    }

    private Resource createResource(String fileName) {
        Resource resource = new Resource();
        resource.setS3Url("https://test-bucket.s3.amazonaws.com/" + fileName);
        return resource;
    }
}