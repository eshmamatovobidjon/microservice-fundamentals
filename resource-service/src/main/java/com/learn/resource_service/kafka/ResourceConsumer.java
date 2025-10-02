package com.learn.resource_service.kafka;

import com.learn.resource_service.service.ResourceService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ResourceConsumer {

    private final ResourceService resourceService;

    public ResourceConsumer(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @KafkaListener(topics = "resource-processed", groupId = "resource-service-group")
    public void consume(Long resourceId) {
        System.out.println("Received: " + resourceId);
        resourceService.process(resourceId);
    }
}
