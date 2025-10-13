package com.learn.resource_service.unit.service.impl;

import com.learn.resource_service.client.SongServiceClient;
import com.learn.resource_service.dto.StorageDTO;
import com.learn.resource_service.dto.StorageType;
import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.repository.ResourceRepository;
import com.learn.resource_service.kafka.ResourceProducer;
import com.learn.resource_service.client.S3Service;
import com.learn.resource_service.service.impl.ResourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.Optional;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceServiceImplTest {

    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private S3Service s3Service;
    @Mock
    private ResourceProducer resourceProducer;
    @Mock
    private SongServiceClient songServiceClient;

    @InjectMocks
    private ResourceServiceImpl resourceService;

    private StorageDTO stagingStorage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        stagingStorage = StorageDTO.builder()
                .id(1L)
                .storageType(StorageType.STAGING)
                .bucket("test-bucket")
                .path("staging/")
                .build();
    }

    @Test
    void uploadResource_success() throws Exception{
        byte[] mp3Data = getClass().getResourceAsStream("/test.mp3").readAllBytes();
        when(s3Service.uploadMp3(any(), anyString(), any())).thenReturn("https://bucket.s3.amazonaws.com/file.mp3");
        when(resourceRepository.save(any())).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(s3Service.fileExists(anyString(), any())).thenReturn(true);

        Long id = resourceService.uploadResource(mp3Data);

        assertNotNull(id);
        verify(resourceProducer).sendId(1L);
    }

    @Test
    void uploadResource_invalidMp3_throwsException() {
        byte[] invalidData = "notmp3".getBytes();
        assertThrows(IllegalArgumentException.class, () -> resourceService.uploadResource(invalidData));
    }

    @Test
    void getResourceById_success() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setS3Url("https://bucket.s3.amazonaws.com/file.mp3");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(s3Service.fileExists(anyString(), any())).thenReturn(true);

        Resource result = resourceService.getResourceById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getResourceById_notFound_throwsException() {
        when(resourceRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> resourceService.getResourceById(2L));
    }

    @Test
    void deleteResourcesByIds_success() {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setS3Url("https://bucket.s3.amazonaws.com/file.mp3");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        when(s3Service.fileExists("file.mp3", stagingStorage)).thenReturn(false, false);
        doNothing().when(songServiceClient).deleteSongById(1L);
        List<Long> deleted = resourceService.deleteResourcesByIds("1");

        assertEquals(List.of(1L), deleted);
        verify(s3Service).deleteFile("file.mp3", stagingStorage);
        verify(resourceRepository).delete(resource);
    }

    @Test
    void getResourceContent_success() throws Exception {
        Resource resource = new Resource();
        resource.setId(1L);
        resource.setS3Url("https://bucket.s3.amazonaws.com/file.mp3");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        // Load a valid MP3 file for Tika validation
        byte[] mp3Data = getClass().getResourceAsStream("/test.mp3").readAllBytes();
        when(s3Service.downloadFile(anyString(), any())).thenReturn(mp3Data);

        byte[] content = resourceService.getResourceContent(1L);

        assertNotNull(content);
        assertArrayEquals(mp3Data, content);
    }

    @Test
    void getResourceContent_noS3Url_throwsException() {
        Resource resource = new Resource();
        resource.setId(1L);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        assertThrows(RuntimeException.class, () -> resourceService.getResourceContent(1L));
    }
}