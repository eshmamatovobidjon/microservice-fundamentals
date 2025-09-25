package com.learn.resource_processor.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@TestPropertySource(properties = {
        "resource-service.url=localhost",
        "resource-service.port=8081",
        "song-service.url=localhost",
        "song-service.port=8082"
})
@DirtiesContext
@Disabled
class RestClientsComponentTest {

    @Autowired
    private ResourceServiceClient resourceServiceClient;

    @Autowired
    private SongServiceClient songServiceClient;

    private MockRestServiceServer mockServer;

    @Autowired
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    @Test
    @DisplayName("Resource service client should retrieve resource data")
    void resourceServiceClientShouldRetrieveData() {
        // Given
        Long resourceId = 123L;
        byte[] expectedData = "mock mp3 data".getBytes();

        mockServer.expect(requestTo("http://localhost:8081/resources/123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(expectedData, MediaType.APPLICATION_OCTET_STREAM));

        // When
        byte[] actualData = resourceServiceClient.getResourceData(resourceId);

        // Then
        assertThat(actualData).isEqualTo(expectedData);
    }

    @Test
    @DisplayName("Resource service client should retry on failure")
    void resourceServiceClientShouldRetryOnFailure() {
        // Given
        Long resourceId = 456L;
        byte[] expectedData = "mock mp3 data".getBytes();

        mockServer.expect(requestTo("http://localhost:8081/resources/456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockServer.expect(requestTo("http://localhost:8081/resources/456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(expectedData, MediaType.APPLICATION_OCTET_STREAM));

        // When
        byte[] actualData = resourceServiceClient.getResourceData(resourceId);

        // Then
        assertThat(actualData).isEqualTo(expectedData);
    }

    @Test
    @DisplayName("Song service client should save song metadata")
    void songServiceClientShouldSaveSongMetadata() {
        // Given
        SongDTO songDTO = createTestSongDTO();

        mockServer.expect(requestTo("http://localhost:8082/songs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(asJsonString(songDTO), MediaType.APPLICATION_JSON));

        // When & Then
        assertThatCode(() -> songServiceClient.saveSongMetadata(songDTO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Song service client should retry on failure")
    void songServiceClientShouldRetryOnFailure() {
        // Given
        SongDTO songDTO = createTestSongDTO();

        mockServer.expect(requestTo("http://localhost:8082/songs"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        mockServer.expect(requestTo("http://localhost:8082/songs"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(asJsonString(songDTO), MediaType.APPLICATION_JSON));

        // When & Then
        assertThatCode(() -> songServiceClient.saveSongMetadata(songDTO))
                .doesNotThrowAnyException();
    }

    private SongDTO createTestSongDTO() {
        SongDTO songDTO = new SongDTO();
        songDTO.setId(1L);
        songDTO.setName("Test Song");
        songDTO.setArtist("Test Artist");
        songDTO.setAlbum("Test Album");
        songDTO.setDuration("03:45");
        songDTO.setYear("2023");
        return songDTO;
    }

    private String asJsonString(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
