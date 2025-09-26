package com.learn.resource_processor.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class ResourceProducer {
    private final KafkaTemplate<String, Long> kafkaTemplate;
    private final String resourceCreatedTopic;

    public ResourceProducer(KafkaTemplate<String, Long> kafkaTemplate,
                            @Value("${kafka.topic.resource-processed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.resourceCreatedTopic = topic;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sendId(Long id) {
        try {
            // wait for ack from Kafka
            var result = kafkaTemplate.send(resourceCreatedTopic, id).get();

            System.out.printf(
                    "Sent ID=%s to topic=%s, partition=%d, offset=%d%n",
                    id,
                    resourceCreatedTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (Exception e) {
            System.err.printf("Failed to send ID=%s to topic=%s. Reason: %s%n",
                    id, resourceCreatedTopic, e.getMessage());
            e.printStackTrace();
        }
    }
}
