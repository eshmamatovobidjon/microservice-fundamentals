package com.learn.resource_processor.integration;

import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import com.learn.resource_processor.kafka.ResourceProducer;
import com.learn.resource_processor.service.impl.ResourceProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Disabled
class ResourceProcessorServiceIntegrationTest {

    @MockitoBean
    private ResourceServiceClient resourceServiceClient;

    @MockitoBean
    private SongServiceClient songServiceClient;

    @MockitoBean
    private ResourceProducer resourceProducer;

    private ResourceProcessorServiceImpl resourceProcessorService;

    private byte[] validMp3Data;

    @BeforeEach
    void setUp() {
        resourceProcessorService = new ResourceProcessorServiceImpl(resourceServiceClient, songServiceClient, resourceProducer);
        validMp3Data = createValidMp3Data();
    }

    @Test
    void shouldProcessResourceAndSaveSongMetadata() {
        Long resourceId = 123L;
        when(resourceServiceClient.getResourceData(resourceId)).thenReturn(validMp3Data);

        resourceProcessorService.process(resourceId);

        ArgumentCaptor<SongDTO> songCaptor = ArgumentCaptor.forClass(SongDTO.class);
        verify(songServiceClient, times(1)).saveSongMetadata(songCaptor.capture());

        SongDTO songDTO = songCaptor.getValue();
        assertThat(songDTO.getId()).isEqualTo(123L);
        assertThat(songDTO.getName()).isNotEmpty();
        assertThat(songDTO.getArtist()).isNotEmpty();
        assertThat(songDTO.getAlbum()).isNotEmpty();
        assertThat(songDTO.getDuration()).isNotEmpty();
        assertThat(songDTO.getYear()).isNotEmpty();
    }

    private byte[] createValidMp3Data() {
        byte[] mp3Header = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x00};
        byte[] fullMp3 = new byte[1024];
        System.arraycopy(mp3Header, 0, fullMp3, 0, mp3Header.length);
        return fullMp3;
    }
}
