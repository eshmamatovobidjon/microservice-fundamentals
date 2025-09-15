package com.learn.resource_processor.contract;

import com.learn.resource_processor.service.ResourceProcessorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

@SpringBootTest
@AutoConfigureStubRunner(
        ids = {
                "com.learn:resource-service:+:stubs:8081",
                "com.learn:song-service:+:stubs:8082"
        },
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class ResourceProcessorContractTest {

    @Autowired
    private ResourceProcessorService resourceProcessorService;

    @Test
    void shouldProcessStubbedResource() {
        Long resourceId = 1L;
        // This will hit stubbed resource-service (not real one!)
        resourceProcessorService.process(resourceId);
    }
}
