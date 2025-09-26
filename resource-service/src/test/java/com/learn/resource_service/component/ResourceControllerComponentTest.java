package com.learn.resource_service.component;

import com.learn.resource_service.client.S3Service;
import com.learn.resource_service.client.SongServiceClient;
import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageType;
import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.kafka.ResourceProducer;
import com.learn.resource_service.repository.ResourceRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = "resource-created")
@Testcontainers
class ResourceControllerComponentTest {

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
    private TestRestTemplate restTemplate;

    @Autowired
    private ResourceRepository resourceRepository;

    @MockitoBean // Mock only external S3 service
    private S3Service s3Service;

    @MockitoBean
    private SongServiceClient songServiceClient;

    @Autowired // Real Kafka producer
    private ResourceProducer resourceProducer;

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
    @DisplayName("Component Test: POST /resources should upload with real DB and Kafka")
    void uploadResource_EndToEndWithRealInfrastructure_Success() {
        // Given
        String s3Url = "https://test-bucket.s3.amazonaws.com/component-test.mp3";
        when(s3Service.uploadMp3(any(byte[].class), anyString(), any())).thenReturn(s3Url);
        when(s3Service.fileExists(anyString(), any())).thenReturn(true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(validMp3Data, headers);

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity("/resources", request, Map.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Integer resourceId = (Integer) response.getBody().get("id");
        assertNotNull(resourceId);

        // Verify real database storage
        Assertions.assertTrue(resourceRepository.existsById(resourceId.longValue()));
        Resource savedResource = resourceRepository.findById(resourceId.longValue()).get();
        assertEquals(s3Url, savedResource.getS3Url());

        // Verify S3 interactions
        verify(s3Service).fileExists(anyString(), any());
    }

    @Test
    @DisplayName("Component Test: GET /resources/{id}/info should query real DB")
    void getResourceInfo_WithRealDatabase_Success() {
        // Given - Create resource directly in database
        Resource resource = new Resource();
        resource.setS3Url("https://test-bucket.s3.amazonaws.com/info-test.mp3");
        Resource savedResource = resourceRepository.save(resource);

        when(s3Service.fileExists("info-test.mp3", stagingStorage)).thenReturn(true);

        // When
        ResponseEntity<Resource> response = restTemplate.getForEntity(
                "/resources/" + savedResource.getId() + "/info",
                Resource.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(savedResource.getId(), response.getBody().getId());
        assertEquals(savedResource.getS3Url(), response.getBody().getS3Url());

        // Verify S3 file existence check
        verify(s3Service).fileExists("info-test.mp3", stagingStorage);
    }

    @Test
    @DisplayName("Component Test: GET /resources/{id}/info should return 404 when not in DB")
    void getResourceInfo_NotFoundInRealDB_Returns404() {
        // Given
        Long nonExistentId = 999L;

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/resources/" + nonExistentId + "/info",
                String.class
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        // Verify no S3 interactions when DB lookup fails
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("Component Test: GET /resources/{id} should download content via real DB and mocked S3")
    void downloadResourceContent_WithRealDBAndMockedS3_Success() {
        // Given
        Resource resource = new Resource();
        resource.setS3Url("https://test-bucket.s3.amazonaws.com/download-test.mp3");
        Resource savedResource = resourceRepository.save(resource);

        when(s3Service.downloadFile("download-test.mp3", stagingStorage)).thenReturn(validMp3Data);

        // When
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/resources/" + savedResource.getId(),
                byte[].class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("audio/mpeg", response.getHeaders().getContentType().toString());
        assertArrayEquals(validMp3Data, response.getBody());

        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
        assertTrue(contentDisposition.contains("attachment"));
        assertTrue(contentDisposition.contains("resource_" + savedResource.getId() + ".mp3"));

        // Verify S3 download was called
        verify(s3Service).downloadFile("download-test.mp3", stagingStorage);
    }

    @Test
    @DisplayName("Component Test: DELETE /resources should delete from real DB with S3 cleanup")
    void deleteResources_WithRealDB_Success() {
        // Given - Create multiple resources in real database
        Resource resource1 = resourceRepository.save(createResource("delete1.mp3"));
        Resource resource2 = resourceRepository.save(createResource("delete2.mp3"));
        Resource resource3 = resourceRepository.save(createResource("delete3.mp3"));

        when(s3Service.fileExists(anyString(), any())).thenReturn(false); // Simulate successful S3 deletion
        doNothing().when(songServiceClient).deleteSongById(anyLong());
        String csvIds = String.format("%d,%d,%d", resource1.getId(), resource2.getId(), resource3.getId());

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                "/resources?id=" + csvIds,
                HttpMethod.DELETE,
                null,
                Map.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Integer> deletedIds = (List<Integer>) response.getBody().get("ids");
        assertEquals(3, deletedIds.size());

        // Verify real database deletions
        assertFalse(resourceRepository.existsById(resource1.getId()));
        assertFalse(resourceRepository.existsById(resource2.getId()));
        assertFalse(resourceRepository.existsById(resource3.getId()));
        assertEquals(0, resourceRepository.count());

        // Verify S3 cleanup calls
        verify(s3Service).deleteFile("delete1.mp3", stagingStorage);
        verify(s3Service).deleteFile("delete2.mp3", stagingStorage);
        verify(s3Service).deleteFile("delete3.mp3", stagingStorage);
    }

    @Test
    @DisplayName("Component Test: Should handle S3 upload failure with proper HTTP response")
    void uploadResource_S3UploadFails_Returns500() {
        // Given
        when(s3Service.uploadMp3(any(byte[].class), anyString(), any()))
                .thenThrow(new RuntimeException("S3 upload failed"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(validMp3Data, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity("/resources", request, String.class);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // Verify no resource was saved to real database
        assertEquals(0, resourceRepository.count());
    }

    @Test
    @DisplayName("Component Test: Should handle invalid MP3 data with proper HTTP response")
    void uploadResource_InvalidMp3Data_Returns400() {
        // Given
        byte[] invalidData = "not mp3 data".getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(invalidData, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity("/resources", request, String.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // Verify no database or S3 interactions
        assertEquals(0, resourceRepository.count());
        verifyNoInteractions(s3Service);
    }

    private Resource createResource(String fileName) {
        Resource resource = new Resource();
        resource.setS3Url("https://test-bucket.s3.amazonaws.com/" + fileName);
        return resource;
    }
}
