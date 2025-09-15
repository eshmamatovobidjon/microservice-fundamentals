package com.learn.resource_processor.integration;

import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import com.learn.resource_processor.kafka.ResourceConsumer;
import com.learn.resource_processor.service.ResourceProcessorService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9093",
                "port=9093"
        },
        topics = {"resource-created"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=test-resource-processor-group",
        "spring.kafka.consumer.enable-auto-commit=false",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaConsumerIntegrationTest {

    @MockitoSpyBean
    private ResourceConsumer resourceConsumer;

    @MockitoBean
    private ResourceProcessorService resourceProcessorService;

    @MockitoBean
    private ResourceServiceClient resourceServiceClient;

    @MockitoBean
    private SongServiceClient songServiceClient;

    private KafkaProducer<String, Long> testProducer;

    private static final String TOPIC_NAME = "resource-created";
    private static final Long TEST_RESOURCE_ID = 12345L;

    @BeforeEach
    void setUp() {
        // Create test producer
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(
                System.getProperty("spring.embedded.kafka.brokers")
        );
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer.class);

        testProducer = new KafkaProducer<>(producerProps);

        // Reset mocks
        reset(resourceConsumer, resourceProcessorService, resourceServiceClient, songServiceClient);
    }

    @Test
    @Timeout(10)
    void shouldConsumeResourceIdMessage() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyLong());

        // When
        sendMessageToKafka(TEST_RESOURCE_ID);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be consumed within 5 seconds");

        verify(resourceConsumer, times(1)).consume(TEST_RESOURCE_ID);
        verify(resourceProcessorService, times(1)).process(TEST_RESOURCE_ID);
    }

    @Test
    @Timeout(10)
    void shouldConsumeMultipleMessages() throws InterruptedException {
        // Given
        List<Long> resourceIds = Arrays.asList(101L, 102L, 103L);
        CountDownLatch latch = new CountDownLatch(resourceIds.size());

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyLong());

        // When
        for (Long resourceId : resourceIds) {
            sendMessageToKafka(resourceId);
        }

        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "All messages should be consumed within 10 seconds");

        verify(resourceConsumer, times(3)).consume(anyLong());
        verify(resourceProcessorService, times(3)).process(anyLong());

        // Verify each specific resource ID was processed
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(resourceProcessorService, times(3)).process(captor.capture());

        List<Long> processedIds = captor.getAllValues();
        assertThat(processedIds).containsExactlyInAnyOrderElementsOf(resourceIds);
    }

    @Test
    @Timeout(10)
    void shouldHandleNumericResourceIds() throws InterruptedException {
        // Given
        Long numericResourceId = 999999L;
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyLong());

        // When
        sendMessageToKafka(numericResourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(numericResourceId);
        verify(resourceProcessorService, times(1)).process(numericResourceId);
    }

    @Test
    @Timeout(10)
    void shouldHandleProcessingException() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("Processing failed");
        }).when(resourceProcessorService).process(anyLong());

        // When
        sendMessageToKafka(TEST_RESOURCE_ID);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(TEST_RESOURCE_ID);
        verify(resourceProcessorService, times(1)).process(TEST_RESOURCE_ID);

        // The consumer should have attempted processing despite the exception
        verifyNoMoreInteractions(resourceProcessorService);
    }

    @Test
    @Timeout(10)
    void shouldVerifyConsumerGroupConfiguration() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyLong());

        // When
        sendMessageToKafka(TEST_RESOURCE_ID);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // The fact that the message was consumed confirms the consumer group is working
        verify(resourceConsumer, times(1)).consume(TEST_RESOURCE_ID);
    }

    @Test
    @Timeout(10)
    void shouldVerifyMessageConsumptionWithMockingChain() throws InterruptedException {
        // Given
        Long resourceId = 123L;
        byte[] mockResourceData = "mock-mp3-data".getBytes();
        SongDTO mockSongDTO = new SongDTO();
        mockSongDTO.setName("Test Song");

        CountDownLatch latch = new CountDownLatch(1);

        // Mock the entire chain
        when(resourceServiceClient.getResourceData(resourceId)).thenReturn(mockResourceData);

        doAnswer(invocation -> {
            // Simulate the real service behavior
            Long id = invocation.getArgument(0);
            resourceServiceClient.getResourceData(id);
            songServiceClient.saveSongMetadata(any(SongDTO.class));
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyLong());

        // When
        sendMessageToKafka(resourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(resourceId);
        verify(resourceProcessorService, times(1)).process(resourceId);
    }

    private void sendMessageToKafka(Long resourceId) {
        ProducerRecord<String, Long> record = new ProducerRecord<>(TOPIC_NAME, resourceId);
        testProducer.send(record);
        testProducer.flush();
    }
}