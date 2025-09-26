package com.learn.resource_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.resource_service.client.S3Service;
import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageType;
import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.kafka.ResourceProducer;
import com.learn.resource_service.repository.ResourceRepository;
import com.learn.resource_service.service.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092", // Will be mocked anyway
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.LongDeserializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.LongSerializer"
})
@DirtiesContext
class ResourceServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("resource_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ResourceService resourceService;

    @MockitoBean
    private S3Service s3Service;

    @MockitoBean
    private ResourceProducer resourceProducer;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private byte[] validMp3Data;
    private StorageDTO stagingStorage;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/resources";

        stagingStorage = new StorageDTO();
        stagingStorage.setId(1L);
        stagingStorage.setStorageType(StorageType.STAGING);
        stagingStorage.setBucket("test-bucket");
        stagingStorage.setPath("staging/");

        validMp3Data = createValidMp3Data();

        reset(s3Service, resourceProducer);

        when(s3Service.uploadMp3(any(byte[].class), anyString(), any()))
                .thenReturn("https://test-bucket.s3.amazonaws.com/test-file.mp3");
        when(s3Service.fileExists(anyString(), any())).thenReturn(true);
        when(s3Service.downloadFile(anyString(), any())).thenReturn(validMp3Data);
        doNothing().when(resourceProducer).sendId(anyLong());
    }

    @AfterEach
    void tearDown() {
        resourceRepository.deleteAll();
    }

    @Test
    void shouldUploadResourceSuccessfully() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(validMp3Data, headers);

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();

        Long resourceId = ((Number) response.getBody().get("id")).longValue();

        // Verify database
        Optional<Resource> savedResource = resourceRepository.findById(resourceId);
        assertThat(savedResource).isPresent();
        assertThat(savedResource.get().getS3Url()).isEqualTo("https://test-bucket.s3.amazonaws.com/test-file.mp3");

        // Verify S3 service calls
        verify(s3Service, times(1)).uploadMp3(eq(validMp3Data), anyString(), any());
        verify(s3Service, times(1)).fileExists(anyString(), any());

        // Verify Kafka producer call
        verify(resourceProducer, times(1)).sendId(resourceId);
    }

    @Test
    void shouldFailUploadWithInvalidContentType() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<byte[]> request = new HttpEntity<>(validMp3Data, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify no interactions with external services
        verifyNoInteractions(s3Service, resourceProducer);
        assertThat(resourceRepository.count()).isZero();
    }

    @Test
    void shouldHandleS3UploadFailure() {
        // Given
        when(s3Service.uploadMp3(any(byte[].class), anyString(), any()))
                .thenThrow(new RuntimeException("S3 upload failed"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(validMp3Data, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify cleanup - no database record should exist
        assertThat(resourceRepository.count()).isZero();

        // Verify no Kafka message sent
        verifyNoInteractions(resourceProducer);
    }

    @Test
    void shouldRollbackOnKafkaFailureAndCleanup() {
        // Given
        doThrow(new RuntimeException("Kafka send failed"))
                .when(resourceProducer).sendId(anyLong());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(validMp3Data, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify cleanup was performed
        verify(s3Service, times(1)).deleteFile(anyString(), any());

        // Verify transaction rollback - no database record should exist
        assertThat(resourceRepository.count()).isZero();
    }

    @Test
    void shouldGetResourceByIdSuccessfully() {
        // Given - Create a resource first
        Resource resource = createResourceInDatabase();

        // When
        ResponseEntity<Resource> response = restTemplate.getForEntity(
                baseUrl + "/" + resource.getId() + "/info", Resource.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(resource.getId());
        assertThat(response.getBody().getS3Url()).isEqualTo(resource.getS3Url());

        // Verify S3 existence check
        verify(s3Service, times(1)).fileExists(anyString(), any());
    }

    @Test
    void shouldReturn404ForNonExistentResource() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/99999/info", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldGetResourceContentSuccessfully() {
        // Given
        Resource resource = createResourceInDatabase();

        // When
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                baseUrl + "/" + resource.getId(), byte[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(validMp3Data);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("audio/mpeg"));

        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
        assertThat(contentDisposition).contains("attachment");
        assertThat(contentDisposition).contains("resource_" + resource.getId() + ".mp3");

        // Verify S3 download call
        verify(s3Service, times(1)).downloadFile(anyString(), any());
    }

    @Test
    void shouldReturn404ForNonExistentResourceContent() {
        // When
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                baseUrl + "/99999", byte[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldHandleS3DownloadFailure() {
        // Given
        Resource resource = createResourceInDatabase();
        when(s3Service.downloadFile(anyString(), any()))
                .thenThrow(new RuntimeException("S3 download failed"));

        // When
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                baseUrl + "/" + resource.getId(), byte[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Helper methods
    private Resource createResourceInDatabase() {
        Resource resource = new Resource();
        resource.setS3Url("https://test-bucket.s3.amazonaws.com/test-file.mp3");
        return resourceRepository.save(resource);
    }

    private byte[] createValidMp3Data() {
        byte[] mp3Header = {
                (byte) 0xFF, (byte) 0xFB, // MP3 sync word
                (byte) 0x90, (byte) 0x00, // Additional header bytes
        };

        byte[] fullMp3 = new byte[1024];
        System.arraycopy(mp3Header, 0, fullMp3, 0, mp3Header.length);

        return fullMp3;
    }
}
