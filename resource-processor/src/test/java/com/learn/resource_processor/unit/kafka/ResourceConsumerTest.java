package com.learn.resource_processor.unit.kafka;

import com.learn.resource_processor.kafka.ResourceConsumer;
import com.learn.resource_processor.service.ResourceProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ResourceConsumerTest {

    private ResourceProcessorService resourceProcessorService;
    private ResourceConsumer resourceConsumer;

    @BeforeEach
    void setUp() {
        resourceProcessorService = mock(ResourceProcessorService.class);
        resourceConsumer = new ResourceConsumer(resourceProcessorService);
    }

    @Test
    void consume_withValidResourceId_callsProcess() {
        // Arrange
        Long resourceId = 123L;

        // Act
//        resourceConsumer.consume(resourceId);

        // Assert
        verify(resourceProcessorService, times(1)).process(resourceId);
    }
}
